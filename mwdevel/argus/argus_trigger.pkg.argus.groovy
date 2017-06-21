#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'pap-16', description: 'The branch of the pkg.argus repo' ),
    string(name: 'COMPONENT_LIST', defaultValue: 'pap pdp-pep-common pep-common pdp pep-server pep-api-c pep-api-java pepcli gsi-pep-callout metapackage', description: 'Components to build' ),
    choice(name: 'INCLUDE_BUILD_NUMBER', choices: '1\n0', description: 'Flag to include/exclude build number.')
  ]),
])

def build_number = ''
def pkg_el6
def pkg_el7
def job_to_build

stage('create RPMs'){
  if("${params.INCLUDE_BUILD_NUMBER}" == "1") {
    build_number = new Date().format("yyyyMMddHHmmss")
  }

  def branch = java.net.URLEncoder.encode("${params.PKG_TAG}", "UTF-8")
  job_to_build = "argus-authz/pkg.argus/${branch}"

  parallel(
      'centos6': {
        pkg_el6 = build job: "${job_to_build}", propagate: false, parameters: [
          string(name: 'COMPONENT_LIST', value: "${params.COMPONENT_LIST}"),
          string(name: 'PLATFORM', value: 'centos6'),
          string(name: 'INCLUDE_BUILD_NUMBER', value: "${params.INCLUDE_BUILD_NUMBER}"),
          string(name: 'PKG_BUILD_NUMBER', value: "${build_number}")
        ]
      },
      'centos7': {
        pkg_el7 = build job: "${job_to_build}", propagate: false, parameters: [
          string(name: 'COMPONENT_LIST', value: "${params.COMPONENT_LIST}"),
          string(name: 'PLATFORM', value: 'centos7'),
          string(name: 'INCLUDE_BUILD_NUMBER', value: "${params.INCLUDE_BUILD_NUMBER}"),
          string(name: 'PKG_BUILD_NUMBER', value: "${build_number}")
        ]
      }
      )

  if("FAILURE".equals(pkg_el6.result) && "FAILURE".equals(pkg_el7.result)) {
    currentBuild.result = 'FAILURE'
    slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    sh "exit 1"
  }
}

node('generic'){
  stage('archive'){
    def argus_root = "/mnt/packages/repo/argus"

    try {
      step ([$class: 'CopyArtifact',
        projectName: "${job_to_build}",
        filter: 'repo/centos6/**',
        selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el6.number}"]
      ])

      step ([$class: 'CopyArtifact',
        projectName: "${job_to_build}",
        filter: 'repo/centos7/**',
        selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
      ])

      dir('repo') {
        sh "mkdir -p {el6,el7}/RPMS"

        sh "mv centos6/* el6/RPMS/"
        sh "createrepo el6/RPMS/"
        sh "repoview el6/RPMS/"

        sh "mv centos7/* el7/RPMS/"
        sh "createrepo el7/RPMS/"
        sh "repoview el7/RPMS/"

        sh "mkdir -p ${argus_root}/builds/build_${BUILD_NUMBER}"
        sh "cp -r el6/ el7/ ${argus_root}/builds/build_${BUILD_NUMBER}/"
      }

      dir("${argus_root}"){
        sh "rm -vf ${argus_root}/nightly"
        sh "ln -vs ./builds/build_${BUILD_NUMBER}/ nightly"
      }

      sh "find ${argus_root}/builds/ -maxdepth 1 -type d -ctime +10 -print -exec rm -rf {} \\;"
    }catch(e) {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
      throw e
    }
  }
}
