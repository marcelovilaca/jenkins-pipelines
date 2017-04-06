#!groovy
// name: puppet-modules-analysis

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
])

stage('analyze'){
  parallel(
      "puppet" : {
        build job: 'sonar-scanner-analysis',
        parameters: [
          string(name: 'REPO',           value: 'https://github.com/marcocaberletti/puppet'),
          string(name: 'BRANCH',         value: 'master'),
          string(name: 'PROJECTKEY',     value: 'puppet'),
          string(name: 'PROJECTNAME',    value: 'Puppet Modules'),
          string(name: 'PROJECTVERSION', value: '1.0'),
          string(name: 'SOURCES',        value: 'modules')
        ]
      },
      "argus-authz" : {
        build job: 'sonar-scanner-analysis',
        parameters: [
          string(name: 'REPO',           value: 'https://github.com/argus-authz/argus-mw-devel'),
          string(name: 'BRANCH',         value: 'master'),
          string(name: 'PROJECTKEY',     value: 'argus-mw-devel'),
          string(name: 'PROJECTNAME',    value: 'Argus authz Puppet Module'),
          string(name: 'PROJECTVERSION', value: '1.0'),
          string(name: 'SOURCES',        value: '.')
        ]
      },
      "ci-puppet-modules" : {
        build job: 'sonar-scanner-analysis',
        parameters: [
          string(name: 'REPO',           value: 'https://github.com/cnaf/ci-puppet-modules'),
          string(name: 'BRANCH',         value: 'master'),
          string(name: 'PROJECTKEY',     value: 'ci-puppet-modules'),
          string(name: 'PROJECTNAME',    value: 'Puppet Modules for CNAF CI'),
          string(name: 'PROJECTVERSION', value: '2.0'),
          string(name: 'SOURCES',        value: 'modules')
        ]
      },
      "kubernetes" : {
        build job: 'sonar-scanner-analysis',
        parameters: [
          string(name: 'REPO',           value: 'git@baltig.infn.it:mw-devel/kubernetes-mw-devel.git'),
          string(name: 'BRANCH',         value: 'master'),
          string(name: 'PROJECTKEY',     value: 'kubernetes-mw-devel'),
          string(name: 'PROJECTNAME',    value: 'Kubernetes Puppet Modules'),
          string(name: 'PROJECTVERSION', value: '1.0'),
          string(name: 'SOURCES',        value: '.')
        ]
      },
      "sensu" : {
        build job: 'sonar-scanner-analysis',
        parameters: [
          string(name: 'REPO',           value: 'git@baltig.infn.it:mw-devel/sensu-mw-devel.git'),
          string(name: 'BRANCH',         value: 'master'),
          string(name: 'PROJECTKEY',     value: 'sensu-mw-devel'),
          string(name: 'PROJECTNAME',    value: 'Sensu installation Puppet Modules'),
          string(name: 'PROJECTVERSION', value: '1.0'),
          string(name: 'SOURCES',        value: '.')
        ]
      },
      failFast: false
      )
  currentBuild.result = 'SUCCESS'
}

