//def build_agent = 'bitbuild-maven-oci'
//def mvn_settings = 'bitbuild-maven-settings'


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
  }

  stages {
    stage ('Integration Tests') {
      environment {
        MVN_ARGS = "${mvnArgs}"
      }
      steps {
        sh 'mvn -s $MVN_SETTINGS_XML test $MVN_ARGS'
      }
    }

    stage ('Clean') {
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
