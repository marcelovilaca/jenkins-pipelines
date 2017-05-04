#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    choice(name: 'PRODUCT', choices: 'argus\nindigo-iam', description: 'Product packages'),
    string(name: 'BUILD_NUMBER', defaultValue: '', description: 'Build to promote. Empty for LastStableBuild' ),
    choice(name: 'TARGET', choices: 'beta\nstable', description: 'Target version'),
    string(name: 'REPO_TITLE', defaultValue: '', description: 'Description of the repository in a few word. Platform is appended by default.')
  ]),
])


node('generic'){
  stage('promote'){
    def pkg_root = "/mnt/packages/repo/${params.PRODUCT}"

    def dest_dir = "${pkg_root}/${params.TARGET}"
    def src_dir = "${pkg_root}/nightly"

    if ("" != "${params.BUILD_NUMBER}") {
      src_dir = "${pkg_root}/builds/build_${params.BUILD_NUMBER}"
    }

    sh "rsync -avu ${src_dir}/ ${dest_dir}/"

    sh "createrepo ${dest_dir}/el6/RPMS"
    sh "repoview -t '${params.REPO_TITLE} (CentOS 6)' ${dest_dir}/el6/RPMS"

    sh "createrepo ${dest_dir}/el7/RPMS"
    sh "repoview -t '${params.REPO_TITLE} (CentOS 7)' ${dest_dir}/el7/RPMS"
  }
}
