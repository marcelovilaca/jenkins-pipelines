#!/usr/bin/env groovy
@Library('sd')_
def kubeLabel = getKubeLabel()

pipeline {
  agent {
      kubernetes {
          label "${kubeLabel}"
          cloud 'Kube mwdevel'
          defaultContainer 'runner'
          inheritFrom 'ci-template'
      }
  }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { 
  	cron('@daily') 
  }

  parameters {
    string(name: 'DAYS', defaultValue: '7', description: '' )
  }

  stages {
    stage('run'){
      steps {
          sh "find /srv/scratch/ -maxdepth 1 -type d -mtime +${params.DAYS} -print -exec rm -rf {} \\;"
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

