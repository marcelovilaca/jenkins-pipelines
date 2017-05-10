#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'master', description: 'The branch of the pkg.argus repo' ),
    choice(name: 'INCLUDE_BUILD_NUMBER', choices: '1\n0', description: 'Flag to include/exclude build number.')
  ]),
])


def build_number = ''
def pkg_el7, pkg_deb

try {
  stage('create packages'){

    if("${params.INCLUDE_BUILD_NUMBER}" == "1") {
      build_number = new Date().format("yyyyMMddHHmmss")
    }

    parallel(
        "centos7": {
          pkg_el7 = build job: "pkg.indigo-iam/${params.PKG_TAG}", parameters: [
            string(name: 'PKG_BUILD_NUMBER', value: "${build_number}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: "${params.INCLUDE_BUILD_NUMBER}"),
            string(name: 'PLATFORM', value: "centos7")
          ]
        },
        "ubuntu1604": {
          pkg_deb = build job: "pkg.indigo-iam/${params.PKG_TAG}", parameters: [
            string(name: 'PKG_BUILD_NUMBER', value: "${build_number}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: "${params.INCLUDE_BUILD_NUMBER}"),
            string(name: 'PLATFORM', value: "ubuntu1604")
          ]
        }
        )
  }

  def iam_root = "/mnt/packages/repo/indigo-iam"

  stage('archive RPMs'){
    node('generic'){
      step ([$class: 'CopyArtifact',
        projectName: "pkg.indigo-iam/${params.PKG_TAG}",
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
    }
  }

  stage('archive DEBs'){
    node('generic-ubuntu'){
      step ([$class: 'CopyArtifact',
        projectName: "pkg.indigo-iam/${params.PKG_TAG}",
        filter: 'repo/**',
        selector: [$class: 'SpecificBuildSelector', buildNumber: "${pkg_deb.number}"]
      ])

      dir('repo') {
        def debdir = "xenial/amd64"
        sh "mkdir -p ${debdir}"
        sh "mv ubuntu1604/*.deb ${debdir}"

        dir('xenial') { sh "dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz" }

        sh "mkdir -p ${iam_root}/builds/build_${BUILD_NUMBER}"
        sh "cp -r xenial/ ${iam_root}/builds/build_${BUILD_NUMBER}/"
      }
    }
  }

  stage('update symlink'){
    node('generic'){
      dir("${iam_root}"){
        sh "rm -vf ${iam_root}/nightly"
        sh "ln -vs ./builds/build_${BUILD_NUMBER}/ nightly"
      }
      sh "find ${iam_root}/builds/ -maxdepth 1 -type d -ctime +10 -print -exec rm -rf {} \\;"
    }
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}
