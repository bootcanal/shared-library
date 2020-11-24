package com.github.bootcanal

import com.cloudbees.plugins.credentials.*
import jenkins.models.*

class GitHub {
    public static final CONTEXT_PREFIX = 'continuous-integration/jenkins/'
    public static final CONTEXT_MERGE = 'pr-merge'
    public static final STATUS_MAP = ['SUCCESS':'success', 'FAILURE':'failure','UNSTABLE':'failure', 'ABORTED':'failure']
    public static pullName = ''
    public static branch = 'master'
    public static repository = null
    def static getPassword = { username -> 
      def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials {
          com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials.class,
          jenkins.model.Jenkins.instance
      }
      def c = creds.findResult { it.username == username ? it : null }
      if (c) {
          println "found credential ${c.id} for username ${c.username}"
          def systemCredentialsProvider = jenkins.model.Jenkins.instance.getExtensionList(
              'com.cloudbees.plugins.credentials.SystemCredentialsProvider'
          ).first()
          def password = systemCredentialsProvider.credentials.first().password
          println password
      } else {
          println "could not find credentials for ${username}"
      }
    }

    static init(script) {
        getPassword('DEVCX-GAMBIT-GITHUB')
        repository = ['owner':'bootcanal', 'repo':'']
    }

    def status(script) {
        script.echo "######1: latest branch: ${branch}"
        branch = 'status'
    }

    def review(script) {
        script.echo "#####2: latest branch: ${branch}"
        branch = 'review'
    }

    def tag(script) {
        script.echo "#####2: latest branch: ${branch}"
        branch = 'review'
    }



    static statusHandle(script, checkName) {
        checkStatus(script, checkName)
    }

    static checkStatus(script, check) {
        def checkName = getCheckName(check)
        def state = getState(script)
        script.echo "state: ${state}"
        script.echo "owner: ${repository['owner']}"
        script.echo "repo: ${repository['repo']}"

    }

    static String getCheckName(String name) {
        name = name?.trim()
        if (name.length() == 0) {
            return CONTEXT_PREFIX + CONTEXT_MERGE
        }
        return CONTEXT_PREFIX + name
    }

    static String getState(script) {
        def result = script.currentBuild.currentResult
        if (result == null) {
            script.error "currentBuild.currentResult check null"
            return ''
        }
        def state = STATUS_MAP[result]
        if (state == null) {
            script.error "currentBuild.currentResult parse null: ${state}"
            return ''
        }
        if (state.length() == 0) {
            script.error "currentBuild.currentResult undefined: ${state}"
            return ''
        }
        return state
    }

    def checkPR(token, organization, repo, commit_id, state) {
        def _context = 'continuous-integration/jenkins/unit-test'
        def pr_url = "https://api.github.com/repos/${organization}/${repo}/statuses/${commit_id}"
        def payload = JsonOutput.toJson([context: ${_context}, state: ${state}, target_url: 'https://github.com/bootcanal/canal', description: ${state}])
        sh("curl -XPOST -H \"Authorization: token ${token}\" ${pr_url} -d ${payload}")
    }
}
