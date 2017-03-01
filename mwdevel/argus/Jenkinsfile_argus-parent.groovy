#!groovy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  pipelineTriggers([cron('H H/12 * * *')]),
])

stage('prepare'){
  node('generic'){
    git branch: '1_7', url: 'https://github.com/argus-authz/argus-parent.git'
    sh 'sed -i \'s#radiohead\\.cnaf\\.infn\\.it:8081\\/nexus\\/content\\/repositories#nexus\\.default\\.svc\\.cluster\\.local\\/repository#g\' pom.xml'
    stash include: './*', name: 'code'
  }
}

stage('deploy'){
  node('maven') {
    unstash 'code'
    sh "mvn clean -U -B deploy"
  }
}

