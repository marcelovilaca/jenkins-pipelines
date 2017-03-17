#!groovy
// name iam-login-service-kube-deploy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    choice(name: 'ENVIRONMENT', choices:      'STAGING\nPRODUCTION', description: ''),
    string(name: 'IAM_IMAGE',   defaultValue: 'cloud-vm128.cloud.cnaf.infn.it/indigoiam/iam-login-service:v0.5.0-latest', description: ''),
    choice(name: 'CONTEXT',     choices:      'dev\nprod', description: 'Context infrastructure'),
  ]),
])


def repo = 'git@baltig.infn.it:caberletti/kube_deployments.git'
def directory = 'iam/staging/iam-login-service'
def namespace = 'staging'
def nfs_server = '10.0.0.30'
def mail_server = 'postfix.default.svc'

if ("PRODUCTION" == "${params.ENVIRONMENT}") {
  repo = "git@baltig.infn.it:mw-devel/iam-test.indigo-datacloud.eu.git"
  directory = 'iam-login-service'
  namespace = 'indigo'
  mail_server = 'doctorwho.cnaf.infn.it'
}

node("kubectl"){
  stage('Deploy IAM') {
    git "${repo}"

    def context_opts = ''
    if('dev' == "${params.CONTEXT}") {
      context_opts = '--context dev'
      nfs_server = '10.0.0.13'
      mail_server = 'postfix.default.svc'
    }

    dir("${directory}") {
      withEnv([
        "IAM_IMAGE=${params.IAM_IMAGE}",
        "NAMESPACE=${namespace}",
        "CONTEXT_OPTS=${context_opts}",
        "NFS_SERVER=${nfs_server}",
        "MAIL_SERVER=${mail_server}"
      ]){
        try {
          sh '''
            kubectl ${CONTEXT_OPTS} apply -f iam-db.secret.yaml -f iam-google.secret.yaml -f iam-ssl.secret.yaml --namespace=${NAMESPACE}

            envsubst < ../scheletons/iam.deploy.yaml.tmpl > iam.deploy.yaml
            cat iam.deploy.yaml

            kubectl ${CONTEXT_OPTS} apply -f iam.deploy.yaml --namespace=${NAMESPACE}
            kubectl ${CONTEXT_OPTS} apply -f iam.svc.yaml --namespace=${NAMESPACE}
            kubectl ${CONTEXT_OPTS} apply -f iam.ingress.yaml --namespace=${NAMESPACE}
          '''

          timeout(time: 5, unit: 'MINUTES') { sh "kubectl ${context_opts} rollout status deploy/iam --namespace=${namespace} | grep -q 'successfully rolled out'" }

          currentBuild.result = 'SUCCESS'
        }catch(error){
          withEnv(["NAMESPACE=${namespace}",]){ sh "kubectl ${context_opts} rollout undo deployment/iam --namespace=${namespace}" }
          currentBuild.result = 'FAILURE'
        }
      }
    }
  }
}
