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
    stage ('Init Build') {
      steps {
        mvnArgs = getChangeSetDirs(currentBuild.changeSets)
        echo "-pl ${mvnArgs}" 
      }
    }

    stage ('Build/Test') {
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML install -P$ENV_PROFILE'
      }
    }

    stage ('Container Image/Push to Registry') {
      environment {
        REGISTRY_AUTH_FILE = credentials("${REGISTRY_CREDS}")
      }
      steps {
        //sh 'mvn -s $MVN_SETTINGS_XML exec:exec@oci-image-deploy -P$ENV_PROFILE,oci-image'
        sh 'mvn -s $MVN_SETTINGS_XML -DskipTests=true deploy -P$ENV_PROFILE,oci-image'
      }
    }

    stage ('Clean') {
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML clean -Poci-image'
      }
    }
  }
}
}
