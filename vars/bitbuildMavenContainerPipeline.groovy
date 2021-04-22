//def build_agent = 'bitbuild-maven-oci'
//def mvn_settings = 'bitbuild-maven-settings'
//def registry_creds_prefix = 'bitbuild-registry-auth-json'

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

def mvnArgs = ""
def scmVars

pipeline {
  agent {
    label pipelineParams.build_agent
  }

  environment {
    ENV_PROFILE = bitbuildUtil.getEnvProfile(BRANCH_NAME)
    MVN_SETTINGS_XML = credentials("${pipelineParams.mvn_settings}")
    REGISTRY_CREDS = "${pipelineParams.registry_creds_prefix}-${env.ENV_PROFILE}"
  }

  stages {
    stage ('Checkout') {
      scmVars = checkout scm
    }
    
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

    stage ('Build/UnitTest') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        echo "URL: ${scmVars.GIT_URL}"
        echo "BRANCH: ${scmVars.GIT_BRANCH}"
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
