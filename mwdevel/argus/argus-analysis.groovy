#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
])

def argus_repo = 'https://github.com/argus-authz'
def pap, pdp, pep_server, pdp_pep_common, pep_common, pep_api_java

stage('analyze'){
  parallel(
      "pap" : {
        pap = build job: 'sonar-maven-analysis', propagate: false,
        parameters: [
          string(name: 'REPO',   value: "${argus_repo}/argus-pap"),
          string(name: 'BRANCH', value: '1_7'),
        ]
      },
      "pdp" : {
        pdp = build job: 'sonar-maven-analysis', propagate: false,
        parameters: [
          string(name: 'REPO',   value: "${argus_repo}/argus-pdp"),
          string(name: 'BRANCH', value: '1_7'),
        ]
      },
      "pep-server" : {
        pep_server = build job: 'sonar-maven-analysis', propagate: false,
        parameters: [
          string(name: 'REPO',   value: "${argus_repo}/argus-pep-server"),
          string(name: 'BRANCH', value: 'iota-ca-support'),
        ]
      },
      "pdp-pep-common" : {
        pdp_pep_common = build job: 'sonar-maven-analysis', propagate: false,
        parameters: [
          string(name: 'REPO',   value: "${argus_repo}/argus-pdp-pep-common"),
          string(name: 'BRANCH', value: '1_5'),
        ]
      },
      "pep-common" : {
        pep_common = build job: 'sonar-maven-analysis', propagate: false,
        parameters: [
          string(name: 'REPO',   value: "${argus_repo}/argus-pep-common"),
          string(name: 'BRANCH', value: '2_3'),
        ]
      },
      "pep-api-java" : {
        pep_api_java = build job: 'sonar-maven-analysis', propagate: false,
        parameters: [
          string(name: 'REPO',   value: "${argus_repo}/argus-pep-api-java"),
          string(name: 'BRANCH', value: '2_3'),
        ]
      },
      failFast: false
      )

  def jobs = [
    pap,
    pdp,
    pep_server,
    pdp_pep_common,
    pep_common,
    pep_api_java
  ]
  def result = 'SUCCESS'

  for(int i=0; i<jobs.size(); i++) {
    def job_res = jobs.get(i).result
    if(!'SUCCESS'.equals(job_res)) {
      result = job_res
    }
  }

  currentBuild.result = result
}
