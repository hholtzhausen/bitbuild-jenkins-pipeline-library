//def build_agent = 'bitbuild-maven-oci'
//def mvn_settings = 'bitbuild-maven-settings'
//def registry_creds_prefix = 'bitbuild-registry-auth-json'
//def git_crypt_credentials = 'my-git-crypt-key'
//def dr_profile = 'dr'


def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

def mvnArgs = ""
def projectVersion
def gitCredential = pipelineParams.git_credentials
def gitCryptKeyName = bitbuildUtil.getPropOrDefault({pipelineParams.git_crypt_credentials},"")
def drProfile = bitbuildUtil.getPropOrDefault({pipelineParams.dr_profile},"")
def changeSetOnly = bitbuildUtil.getPropOrDefault({pipelineParams.change_set_only},"true")

pipeline {
  agent {
    label pipelineParams.build_agent
  }

  environment {
    ENV_PROFILE = bitbuildUtil.getEnvProfile(BRANCH_NAME)
    DR_PROFILE = "${drProfile}"
    MVN_SETTINGS_XML = credentials("${pipelineParams.mvn_settings}")
    REGISTRY_CREDS = "${pipelineParams.registry_creds_prefix}-${env.ENV_PROFILE}"
    DR_REGISTRY_CREDS = "${pipelineParams.registry_creds_prefix}-${env.DR_PROFILE}"
    CHANGE_SET_ONLY = "${changeSetOnly}"
  }

  stages {
    stage ('Init Build') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      steps {
        script {
          if(env.ENV_PROFILE != 'prod')
          {
            if(env.CHANGE_SET_ONLY == 'true')
            {
              def dirs = bitbuildUtil.getChangeSetDirs(currentBuild.changeSets)

              if(dirs.length() > 0)
                mvnArgs = "-pl .,${dirs}"
            }
          }
        }
      }
    }

    stage ('Unlock Secrets') {
      when {
        expression { return gitCryptKeyName }
      }
      environment {
        GIT_CRYPT_CREDS = credentials("${gitCryptKeyName}")
      }
      steps {
        sh 'git-crypt unlock $GIT_CRYPT_CREDS'
      }
    }

    stage ('Build/UnitTest') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML install -P$ENV_PROFILE $MVN_ARGS'
      }
    }

    stage ('Container Image/Push to Registry') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        MVN_ARGS = "${mvnArgs}"
        REGISTRY_AUTH_FILE = credentials("${REGISTRY_CREDS}")
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML -DskipTests=true deploy -P$ENV_PROFILE,oci-image $MVN_ARGS'
      }
    }

    stage ('Push to Registry (DR)') {
      when {
          expression { drProfile && env.ENV_PROFILE == 'prod' }
      }
      environment {
        REGISTRY_AUTH_FILE = credentials("${DR_REGISTRY_CREDS}")
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML -DskipTests=true exec:exec@oci-image-deploy -P$DR_PROFILE,oci-image'
      }
    }

    stage ('Tag Release') {
      when {
        environment name: 'ENV_PROFILE', value: 'prod'
      }
      steps {
        script {
          projectVersion = 
              sh(returnStdout: true, 
                 script: 'mvn -s $MVN_SETTINGS_XML help:evaluate -Dexpression=project.version -DforceStdout -q -pl .')
        }

        sh "git tag -a ${projectVersion} -m 'Tagging release ${projectVersion} from pipeline'"

        sshagent([ gitCredential ]) {
          sh "git push origin ${projectVersion} HEAD:${BRANCH_NAME}"
        }
      }
    }

    stage ('Clean') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML clean -Poci-image $MVN_ARGS'
      }
    }
  }
}
}
