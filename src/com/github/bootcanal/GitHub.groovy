package com.github.bootcanal

class GitHub {
    def commitId() {
        return sh(script: "git rev-parse HEAD", returnStdout: true)
    }
    def checkPR(String token, String organization, String repo, String commit_id, String state) {
        def pr_context = 'continuous-integration/jenkins/unit-test'
        def pr_url = "https://api.github.com/repos/${organization}/${repo}/statuses/${commit_id}"
        def payload = JsonOutput.toJson([context: ${pr_context}, state: ${state}, target_url: 'https://github.com/bootcanal/canal', description: ${state}])
        sh("curl -XPOST -H \"Authorization: token ${token}\" ${pr_url} -d ${payload}")
    }
}
