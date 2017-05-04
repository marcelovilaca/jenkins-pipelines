#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'master', description: 'The branch of the pkg.argus repo' ),
    booleanParam(name:'INCLUDE_PKG_BUILD_NUMBER', defaultValue: true, description: 'When true, creates packages which include a build number in their version.')
  ]),
])


def component_list = 'iam-login-service'
def build_number = ''
def pkg_el7


stage('create RPMs'){

  if("${params.INCLUDE_PKG_BUILD_NUMBER}" == true) {
    build_number = new Date().format("yyyyMMddHHmmss")
  }

  parallel(
      'centos7': {
        pkg_el7 = build job: 'pkg.indigo-iam/master', propagate: false, parameters: [
          string(name: 'COMPONENTS', value: "${component_list}"),
          string(name: 'PLATFORM', value: 'centos7'),
          string(name: 'PKG_BUILD_NUMBER', value: "${build_number}")
        ]
      }
      )

  if("FAILURE".equals(pkg_el7.result)) {
    currentBuild.result = 'FAILURE'
  }
}

node('generic'){
  stage('archive'){
    def iam_root = "/mnt/packages/repo/indigo-iam"

    step ([$class: 'CopyArtifact',
      projectName: 'pkg.indigo-iam/master',
      filter: 'repo/centos7/**',
      selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_el7.number}"]
    ])

    dir('repo') {
      sh 'mkdir -p el7/RPMS'

      sh 'mv centos7/* el7/RPMS/'
      sh "createrepo el7/RPMS/"
      sh "repoview el7/RPMS/"

      sh "mkdir -p ${iam_root}/builds/build_${BUILD_NUMBER}"
      sh "cp -r el7/ ${iam_root}/builds/build_${BUILD_NUMBER}/"
    }

    sh """
     cd ${iam_root} 
     rm -vf ${iam_root}/nightly 
     ln -s ./builds/build_${BUILD_NUMBER}/ nightly
    """

    sh "find ${iam_root}/builds/ -maxdepth 1 -type d -ctime +10 -print -exec rm -rf {} \\;"
  }
}
