package com.github.bootcanal

class GitHub {
    def String commitId() {
        return sh(script: "git rev-parse HEAD", returnStdout: true)
    }
    def checkPR(token, organization, repo, commit_id, state) {
        def _context = 'continuous-integration/jenkins/unit-test'
        def pr_url = "https://api.github.com/repos/${organization}/${repo}/statuses/${commit_id}"
        def payload = JsonOutput.toJson([context: ${_context}, state: ${state}, target_url: 'https://github.com/bootcanal/canal', description: ${state}])
        sh("curl -XPOST -H \"Authorization: token ${token}\" ${pr_url} -d ${payload}")
    }
}
