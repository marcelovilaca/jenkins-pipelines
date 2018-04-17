#!/usr/bin/env groovy

pipeline {
  agent { label 'kubectl' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  parameters {
    string(name: 'DAYS', defaultValue: '7', description: '' )
  }

  stages {
    stage('clean'){
      steps {
        container('kubectl-runner'){
          sh "find /srv/scratch/ -maxdepth 1 -type d -ctime +${params.DAYS} -print -exec rm -rf {} \\;"
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}

