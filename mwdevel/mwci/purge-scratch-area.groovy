pipeline {
  agent { label 'kubectl' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@weekly') }

  parameters {
    string(name: 'DAYS', defaultValue: '30', description: '' )
  }

  stages {
    stage('clean'){
      steps {
        sh "find /srv/scratch/ -maxdepth 1 -type d -ctime +${params.DAYS} -print -exec rm -rf {} \\;"
        script { currentBuild.result = 'SUCCESS' }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
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

