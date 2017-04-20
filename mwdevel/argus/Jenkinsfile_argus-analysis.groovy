#!groovy
// argus-analysis

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
])

def argus_repo = 'https://github.com/argus-authz'

stage('analyze'){
  parallel(
      "pap" : {
        build job: 'sonar-maven-analysis',
        parameters: [
          string(name: 'REPO',           value: "${argus_repo}/argus-pap"),
          string(name: 'BRANCH',         value: '1_7'),
        ]
      },
      "pdp" : {
        build job: 'sonar-maven-analysis',
        parameters: [
          string(name: 'REPO',           value: "${argus_repo}/argus-pdp"),
          string(name: 'BRANCH',         value: '1_7'),
        ]
      },
      "pep-server" : {
        build job: 'sonar-maven-analysis',
        parameters: [
          string(name: 'REPO',           value: "${argus_repo}/argus-pep-server"),
          string(name: 'BRANCH',         value: 'iota-ca-support'),
        ]
      },
      "pdp-pep-common" : {
        build job: 'sonar-maven-analysis',
        parameters: [
          string(name: 'REPO',           value: "${argus_repo}/argus-pdp-pep-common"),
          string(name: 'BRANCH',         value: '1_5'),
        ]
      },
      "pep-common" : {
        build job: 'sonar-maven-analysis',
        parameters: [
          string(name: 'REPO',           value: "${argus_repo}/argus-pep-common"),
          string(name: 'BRANCH',         value: '2_3'),
        ]
      },
      "pep-api-java" : {
        build job: 'sonar-maven-analysis',
        parameters: [
          string(name: 'REPO',           value: "${argus_repo}/argus-pep-api-java"),
          string(name: 'BRANCH',         value: '2_3'),
        ]
      },
      failFast: false
      )
  currentBuild.result = 'SUCCESS'
}
