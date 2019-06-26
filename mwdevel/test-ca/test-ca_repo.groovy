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
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timeout(time: 1, unit: 'HOURS')
  }

  stages {
    stage('import_artifacts') {
      steps {
        cleanWs()
          step([
              $class: 'CopyArtifact',
              projectName: "test-ca/master",
              filter: '**',
              fingerprintArtifacts: true,
              target: './yum'
          ]);
      }
    }
    stage('createRepo') {
      steps {
        sh 'createrepo -v yum'

        script {
          def sourceRepoStr = """[Test-CA]
name=Test-CA
baseurl=$JOB_URL/lastSuccessfulBuild/artifact/yum/
protect=1
enabled=1
priority=1
gpgcheck=0
sslverify=0
"""
          writeFile file: "test-ca.repo", text: "${sourceRepoStr}"
        }

        archiveArtifacts 'yum/*.rpm, yum/repodata/*, test-ca.repo'
      }
    }
    stage('result') {
      steps {
        script {
          currentBuild.result = "SUCCESS"
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
    unstable {
      slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Unstable (<${env.BUILD_URL}|Open>)"
    }
    changed {
      script {
        if ('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
