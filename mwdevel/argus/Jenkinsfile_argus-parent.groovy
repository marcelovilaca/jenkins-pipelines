#!groovy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'BRANCH', defaultValue: '1_7_1', description: '' )
  ]),
])

stage('prepare'){
  node('generic'){
    git branch: "${params.BRANCH}", url: 'https://github.com/argus-authz/argus-parent.git'
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

