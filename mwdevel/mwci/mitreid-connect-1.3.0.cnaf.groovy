#!/usr/bin/env groovy

pipeline {
  agent { label 'maven' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  parameters {
    string(name: 'BRANCH', defaultValue: 'devel', description: '' )
  }

  stages {
    stage('prepare'){
      steps {
        git branch: "${params.BRANCH}", url: 'https://github.com/indigo-iam/OpenID-Connect-Java-Spring-Server.git'
        sh 'sed -i \'s#http:\\/\\/radiohead\\.cnaf\\.infn\\.it:8081\\/nexus\\/content\\/repositories#https:\\/\\/repo\\.cloud\\.cnaf\\.infn\\.it\\/repository#g\' pom.xml'
      }
    }

    stage('deploy'){
      steps {
        sh "mvn -U -B clean deploy"
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
