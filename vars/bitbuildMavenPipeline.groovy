//def build_agent = 'bitbuild-maven-oci'
//def mvn_settings = 'bitbuild-maven-settings'
//def registry_creds_prefix = 'bitbuild-registry-auth-json'
//def git_crypt_credentials = 'my-git-crypt-key'


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
String changeSetOnly = bitbuildUtil.getPropOrDefault({pipelineParams.change_set_only},"true")

pipeline {
  agent {
    label pipelineParams.build_agent
  }

  environment {
    ENV_PROFILE = bitbuildUtil.getEnvProfile(BRANCH_NAME)
    MVN_SETTINGS_XML = credentials("${pipelineParams.mvn_settings}")
  }

  stages {
    stage ('Init Build') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      steps {
        script {
   
          if(changeSetOnly.equals("true"))
          {
            def dirs = bitbuildUtil.getChangeSetDirs(currentBuild.changeSets)

            if(dirs.length() > 0)
              mvnArgs = "-pl .,${dirs}"
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

    stage ('Push to Repository') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML -DskipTests=true deploy -P$ENV_PROFILE $MVN_ARGS'
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
        sh 'mvn -s $MVN_SETTINGS_XML clean $MVN_ARGS'
      }
    }
  }
}
}
