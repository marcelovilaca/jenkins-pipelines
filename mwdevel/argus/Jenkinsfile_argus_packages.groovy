#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'release/1.7.1', description: 'The branch of the pkg.argus repo' ),
    booleanParam(name:'INCLUDE_PKG_BUILD_NUMBER', defaultValue: true, description: 'When true, creates packages which include a build number in their version.')
  ]),
])

def component_list = 'pap pdp-pep-common pep-common pdp pep-server pep-api-c pep-api-java pepcli gsi-pep-callout'
def build_number = ''
def pkg_el6
def pkg_el7

stage('create RPMs'){

  node('generic'){
    if("${params.INCLUDE_PKG_BUILD_NUMBER} == true") {
      build_number = sh (script: "date +'%Y%m%d%H%M%S'", returnStdout: true).trim()
    }
  }

  parallel(
      'centos6': {
        pkg_el6 = build job: 'argus-authz/pkg.argus/release%2F1.7.1', propagate: false, parameters: [
          string(name: 'COMPONENTS', value: "${component_list}"),
          string(name: 'PLATFORM', value: 'centos6'),
          string(name: 'PKG_BUILD_NUMBER', value: "${build_number}")
        ]
      },
      'centos7': {
        pkg_el7 = build job: 'argus-authz/pkg.argus/release%2F1.7.1', propagate: false, parameters: [
          string(name: 'COMPONENTS', value: "${component_list}"),
          string(name: 'PLATFORM', value: 'centos7'),
          string(name: 'PKG_BUILD_NUMBER', value: "${build_number}")
        ]
      }
      )

  if("FAILURE".equals(pkg_el6.result) && "FAILURE".equals(pkg_el7.result)) {
    currentBuild.result = 'FAILURE'
  }
}

node('generic'){
  stage('archive'){
    def remote_cmd = "ssh admin@packages.default.svc.cluster.local"
    def argus_root = "/srv/packages/repo/argus"

    step ([$class: 'CopyArtifact',
      projectName: 'argus-authz/pkg.argus/release%2F1.7.1',
      filter: 'repo/centos6/**',
      selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el6.number}"]
    ])

    step ([$class: 'CopyArtifact',
      projectName: 'argus-authz/pkg.argus/release%2F1.7.1',
      filter: 'repo/centos7/**',
      selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
    ])

    dir('repo') {
      sh 'mkdir -p {el6,el7}/RPMS'

      sh 'mv centos6/* el6/RPMS/'
      sh "createrepo el6/RPMS/"
      sh "repoview el6/RPMS/"

      sh 'mv centos7/* el7/RPMS/'
      sh "createrepo el7/RPMS/"
      sh "repoview el7/RPMS/"

      sh "${remote_cmd} 'mkdir -p ${argus_root}/builds/build_${BUILD_NUMBER}'"
      sh "scp -r el6/ el7/ admin@packages.default.svc.cluster.local:${argus_root}/builds/build_${BUILD_NUMBER}/"
    }

    sh "${remote_cmd} 'cd ${argus_root}; rm -vf ${argus_root}/beta; ln -s ./builds/build_${BUILD_NUMBER}/ beta'"
  }

  //  stage('publish'){
  //
  //  }
}