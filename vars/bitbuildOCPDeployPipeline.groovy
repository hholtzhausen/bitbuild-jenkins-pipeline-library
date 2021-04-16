//def build_agent = 'bitbuild-maven-oci'
//def mvn_settings = 'bitbuild-maven-settings'
//def oc_creds_prefix = 'bitbuild-oc-login'

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

    stage ('Apply Openshift Resources') {
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      environment {
        OC_CREDS = credentials("${OC_CREDS_ID}")
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML -Doc-user=$OC_CREDS_USR -Doc-password=$OC_CREDS_PSW install -P$ENV_PROFILE,ocp-deployment $MVN_ARGS'
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
