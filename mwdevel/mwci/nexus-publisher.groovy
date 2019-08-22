#!/usr/bin/env groovy
@Library('sd')_
def kubeLabel = getKubeLabel()

pipeline{
  agent {
      kubernetes {
          label "${kubeLabel}"
          cloud 'Kube mwdevel'
          defaultContainer 'runner'
          inheritFrom 'ci-template'
      }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    choice(name: 'PRODUCT', choices: 'indigo-iam\nargus', description: 'Product packages')
    choice(name: 'TARGET', choices: 'beta\nstable', description: 'Release target')
  }

  environment{ NEXUS_URL="http://nexus.default.svc.cluster.local" }

  stages {
    stage('push'){
      steps {
        container('runner'){
          withCredentials([
            usernamePassword(credentialsId: 'jenkins-nexus', passwordVariable: 'password', usernameVariable: 'username')
          ]) {
            sh """#!/bin/bash
            echo $PATH
            nexus-assets-remove -u ${username} -p ${password} -H ${env.NEXUS_URL} -r ${params.PRODUCT} -q ${params.TARGET}/el7/RPMS/repodata
            nexus-assets-upload -u ${username} -p ${password} -H ${env.NEXUS_URL} -r ${params.PRODUCT} -d /mnt/packages/repo/${params.PRODUCT}/${params.TARGET}/
            """
          }
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
