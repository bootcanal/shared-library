package com.github.bootcanal

class GitHub {
    def checkPR(String token, String organization, String repo, String commit_id, String state) {
        def url = "https://api.github.com/repos/${organization}/${repo}/statuses/${commit_id}"
        def payload = JsonOutput.toJson([context: 'unit-test', state: ${state}, target_url: 'https://github.com/bootcanal/canal', description: ${state}])
        sh("curl -XPOST -H \"Authorization: token ${token}\" ${url} -d ${payload}")
    }
}
