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
def scmUrl
def projectVersion

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
      when {
        not { environment name: 'ENV_PROFILE', value: 'local' }
      }
      steps {
        script {
          scmUrl = sh(returnStdout: true, script: 'git config remote.origin.url')

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
        echo "URL: ${scmUrl}"
        echo "BRANCH: ${BRANCH_NAME}"
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
        //sh 'mvn -s $MVN_SETTINGS_XML -DskipTests=true deploy -P$ENV_PROFILE,oci-image $MVN_ARGS'
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

        sh "git tag -a ${projectVersion}"
        sh "git push ${projctVersion}"
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
