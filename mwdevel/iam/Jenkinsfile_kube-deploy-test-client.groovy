#!groovy
// name iam-test-client-kube-deploy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    choice(name: 'ENVIRONMENT',           choices:      'STAGING\nPRODUCTION', description: ''),
    string(name: 'IAM_TEST_CLIENT_IMAGE', defaultValue: 'cloud-vm128.cloud.cnaf.infn.it/indigoiam/iam-test-client', description: ''),
    choice(name: 'CONTEXT',               choices:      'dev\nprod', description: 'Context infrastructure'),
  ]),
  //  pipelineTriggers([cron('@daily')]),
])


def repo = 'git@baltig.infn.it:caberletti/kube_deployments.git'
def directory = 'iam/staging/iam-test-client'
def namespace = 'staging'
def nfs_server = '10.0.0.30'

node("kubectl"){

  echo "Params: ENVIROMENT=${params.ENVIRONMENT} CONTEXT=${params.CONTEXT}"

  if ("PRODUCTION" == "${params.ENVIRONMENT}") {
    repo = "git@baltig.infn.it:mw-devel/iam-test.indigo-datacloud.eu.git"
    directory = 'iam-test-client'
    namespace = 'indigo'
  }

  stage('Deploy IAM test client') {
    git "${repo}"

    def context_opts = ''
    if('dev' == "${params.CONTEXT}") {
      context_opts = '--context dev'
      nfs_server = '10.0.0.13'
    }

    dir("${directory}") {
      withEnv([
        "IAM_TEST_CLIENT_IMAGE=${params.IAM_TEST_CLIENT_IMAGE}",
        "NAMESPACE=${namespace}",
        "CONTEXT_OPTS=${context_opts}",
        "NFS_SERVER=${nfs_server}"
      ]){
        try {
          sh '''
            envsubst < ../scheletons/test-client.deploy.yaml.tmpl > test-client.deploy.yaml
            cat test-client.deploy.yaml

            kubectl ${CONTEXT_OPTS} apply -f test-client.deploy.yaml --namespace=${NAMESPACE}
            kubectl ${CONTEXT_OPTS} apply -f test-client.svc.yaml --namespace=${NAMESPACE}
            kubectl ${CONTEXT_OPTS} apply -f test-client.ingress.yaml --namespace=${NAMESPACE}
          '''

          timeout(time: 5, unit: 'MINUTES') {  sh "kubectl ${CONTEXT_OPTS} rollout status deploy/iam-test-client --namespace=${NAMESPACE} | grep -q 'successfully rolled out'" }

          currentBuild.result = 'SUCCESS'
        }catch(error){
          withEnv(["NAMESPACE=${namespace}",]){   sh "kubectl ${CONTEXT_OPTS} rollout undo deployment/iam-test-client --namespace=${NAMESPACE}"   }
          currentBuild.result = 'FAILURE'
        }
      }
    }
  }
}
