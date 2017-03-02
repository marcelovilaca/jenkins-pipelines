#!groovy
// name: docker_build-iam-image

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    string(name: 'TAG',        defaultValue: 'develop',                       description: '' ),
    string(name: 'IMAGE_NAME', defaultValue: 'italiangrid/iam-login-service', description: '')
  ]),
  pipelineTriggers([cron('@daily')]),
])


stage('build'){
  node('docker'){

    git branch: 'master', url: 'https://github.com/marcocaberletti/iam-deployment-test.git'
    dir('iam/iam-be/files'){
      step ([$class: 'CopyArtifact',
        projectName: 'iam-build',
        filter: 'iam-login-service/target/iam-login-service.war',
        target: '.'])

      step ([$class: 'CopyArtifact',
        projectName: 'iam-build',
        filter: 'docker/saml-idp/idp/shibboleth-idp/metadata/idp-metadata.xml',
        target: '.'])

      sh "pwd"
      sh "ls -lh"
    }

    withEnv([
      "TAG=${params.TAG}",
      "IMAGE_NAME=${params.IMAGE_NAME}"
    ]){
      dir('iam/iam-be'){
        sh "find -type f"
        sh "./build-image.sh"
        sh "./push-image.sh"
      }
    }
  }
}
