//def build_agent = 'bitbuild-maven-oci'
//def git_credentials = 'git-credential'
//def mvn_settings = 'bitbuild-maven-settings'
//def oc_creds_prefix = 'bitbuild-oc-login'
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

pipeline {
  agent {
    label pipelineParams.build_agent
  }

  environment {
    ENV_PROFILE = bitbuildUtil.getEnvProfile(BRANCH_NAME)
    MVN_SETTINGS_XML = credentials("${pipelineParams.mvn_settings}")
    OC_CREDS_ID = "${pipelineParams.oc_creds_prefix}-${env.ENV_PROFILE}"
  }

  stages {
    stage ('Init Build') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      steps {
        script {
          def dirs = bitbuildUtil.getChangeSetDirs(currentBuild.changeSets)

          if(dirs.length() > 0)
            mvnArgs = "-pl .,${dirs}"
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

    stage ('Apply Openshift Resources') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        OC_CREDS = credentials("${OC_CREDS_ID}")
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML -Doc-token=$OC_CREDS install -P$ENV_PROFILE,ocp-deployment $MVN_ARGS'
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
