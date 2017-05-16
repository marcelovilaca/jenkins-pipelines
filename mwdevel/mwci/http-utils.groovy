pipeline {
  agent { label 'maven' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  parameters {
    string(name: 'BRANCH', defaultValue: 'jetty9', description: '' )
  }


  stages {
    stage('prepare'){
      steps {
        git branch: "${params.BRANCH}", url: 'https://github.com/italiangrid/https-utils'
        sh 'sed -i \'s#http:\\/\\/radiohead\\.cnaf\\.infn\\.it:8081\\/nexus\\/content\\/repositories#https:\\/\\/repo\\.cloud\\.cnaf\\.infn\\.it\\/repository#g\' pom.xml'
      }
    }

    stage('analysis'){
      steps {
        script{
          def cobertura_opts = 'cobertura:cobertura -Dmaven.test.failure.ignore -DfailIfNoTests=false -Dcobertura.report.format=xml'
          def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'

          withSonarQubeEnv{  sh "mvn clean -U ${cobertura_opts} ${checkstyle_opts} ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}"  }
        }
      }
    }

    stage('deploy'){ steps { sh "mvn clean -U -B deploy" } }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
    unstable {
      slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Unstable (<${env.BUILD_URL}|Open>)"
    }
    changed {
      script{
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
