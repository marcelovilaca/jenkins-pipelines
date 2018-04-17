#!/usr/bin/env groovy

pipeline {
  agent { label 'maven' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@midnight') }

  parameters {
    string(name: 'BRANCH', defaultValue: 'devel', description: '' )
  }

  stages {
    stage('prepare'){
      steps {
        container('maven-runner'){
          git branch: "${params.BRANCH}", url: 'https://github.com/indigo-iam/OpenID-Connect-Java-Spring-Server.git'
          sh 'sed -i \'s#http:\\/\\/radiohead\\.cnaf\\.infn\\.it:8081\\/nexus\\/content\\/repositories#http:\\/\\/nexus\\.default\\.svc\\.cluster\\.local\\/repository#g\' pom.xml'
        }
      }
    }

    stage('deploy'){
      steps {
        container('maven-runner'){
          sh "mvn -U -B clean deploy"
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
