//def build_agent = 'bitbuild-maven-oci'
//def mvn_settings = 'bitbuild-maven-settings'
//def api_credential = 'bitbuild-maven-settings'


def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

def mvnArgs = "-pl integration-tests -Pintegration-tests"

pipeline {
  agent {
    label pipelineParams.build_agent
  }

  environment {
    MVN_SETTINGS_XML = credentials("${pipelineParams.mvn_settings}")
    API_CREDS = credentials("${pipelineParams.api_credential}")
    MVN_ARGS = "${mvnArgs}"
  }

  stages {
    stage ('Integration Tests') {
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML -Doidc-clientid=$API_CREDS_USR -Doidc-clientsecret=$API_CREDS_PSW test $MVN_ARGS'
      }
    }

    stage ('Clean') {
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML clean $MVN_ARGS'
      }
    }
  }
}
}
