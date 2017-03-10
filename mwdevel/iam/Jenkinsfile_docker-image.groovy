#!groovy
// name: docker_build-iam-image

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    string(name: 'TAG',        defaultValue: 'develop',                     description: '' ),
    string(name: 'IMAGE_NAME', defaultValue: 'indigoiam/iam-login-service', description: '')
  ]),
  pipelineTriggers([cron('@daily')]),
])


stage('build'){
  node('docker'){

    git branch: 'master', url: 'https://github.com/indigo-iam/iam.git'

    step ([$class: 'CopyArtifact',
      projectName: 'iam-build',
      filter: 'iam-login-service/target/iam-login-service.war'])

    step ([$class: 'CopyArtifact',
      projectName: 'iam-build',
      filter: 'version.txt'])

    step ([$class: 'CopyArtifact',
      projectName: 'iam-build',
      filter: 'version-commit.txt'])

    def version = readFile('version.txt').trim()
    def version_commit = readFile('version-commit.txt').trim()

    withEnv([
      "IAM_LOGIN_SERVICE_VERSION=${params.TAG}",
      "IAM_LOGIN_SERVICE_IMAGE=${params.IMAGE_NAME}"
    ]){
      dir('iam-login-service/docker'){
        sh "sh build-prod-image.sh"
        sh "sh push-prod-image.sh"
      }
    }
  }
}
