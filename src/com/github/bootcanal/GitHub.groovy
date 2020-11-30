package com.github.bootcanal

import groovy.json.JsonOutput
import java.lang.Exception


class GitHub {
    public static final GITHUB_API = 'https://github.com/api/v3'
    public static final GITHUB_TAG_LENGTH = 7
    /**
     * STATUS_MAP contains Jenkins constant map to GitHub constant
     */
    public static final STATUS_MAP = ['SUCCESS': 'success', 'FAILURE': 'failure', 'UNSTABLE': 'failure', 'ABORTED': 'failure']
    /**
     * EVENT_MAP contains GitHub review event type constant
     */
    public static final EVENT_MAP = ['APPROVE': 'APPROVE', 'REQUEST_CHANGES': 'REQUEST_CHANGES', 'COMMENT': 'COMMENT']
    public static final CONTEXT_PREFIX = 'continuous-integration/jenkins/'
    public static final CONTEXT_BRANCH = 'branch'
    public static final CONTEXT_MERGE = 'pr-merge'
    public static final TAG_COMMIT = 'commit'
    public static final TAG_TREE = 'tree'
    public static final TAG_BLOB = 'blob'
    public static final PULL_REQUEST_CLOSED = 'closed'
    public static final TAG_TYPE_MAP = ['commit': 'commit', 'tree': 'tree', 'blob': 'blob']
    public static final SERVICE_ACCOUNT = 'noreply@bootcanal.com'
    /**
     * github account name
     */
    def String accountName = ''
    /**
     * github access token //e.g. tokenexample
     */
    def String accessToken = ''
    /**
     * git repository url //e.g. https://github.com/bootcanal/canal.git
     */
    def String gitUrl = ''
    /**
     * git repository map //e.g. ['owner': 'bootcanal', 'repo': 'jenkins']
     */
    def Map repository = null
    /**
     * git commit hash
     */
    def String commitHash = ''
    /**
     * git pull request id //32
     */
    def String pullNumber = ''
    /**
     * git branch //e.g. master,main,dev,develop
     */
    def String branch = ''
    /**
     * current build result //e.g. success,failure
     */
    def String state = ''

    /**
     * git check target url
     */
    def String targetUrl = ''
    /**
     * git build number
     */
    def String buildNumber = ''
    /**
     * git branch detail object
     */
    def branchDetail
    /**
     * git pull request detail object
     */
    def prDetail
    /**
     * github tag and docker image tag
     */
    def String tag = ''
    /**
     * github tag message
     */
    def String tagMessage = ''
    /**
     * git pull request merged status
     */
    def boolean merged = false

    /**
     * init
     */
    def init(script) {
        this.setAccountName(script)
        this.setAccessToken(script)
        this.setGitUrl(script)
        this.setRepository(script)
        this.setPullNumber(script)
        this.setBranch(script)
        this.setState(script)
        this.setTargetUrl(script)
        this.setBuildNumber(script)
        this.setBranchDetail(script)
    }

    /**
     * create new github checks
     *
     * @param script
     * @param checkName
     */
    def statusHandle(script, checkName) {
        this.setState(script)
        this.checkStatus(script, checkName)
        if (this.pullNumber.length() > 0) {
            this.checkBranchStatus(script)
        }
    }

    /**
     * create new pull request review
     * 
     * @param script
     * @param eventType
     */
    def reviewHandle(script, eventType) {
        this.setState(script)
        this.checkReview(script, eventType)
    }

    /**
     * create new github repository tag
     *
     * @param script
     */
    def tagHandle(script) {
        this.setState(script)
        this.setTagMessage(script)
        this.generateTag()
        if (!this.merged) {
            return
        }
        this.createTag(script)
    }

    /**
     * check branch merged status
     * 
     * @return boolean
     */
    def boolean getMergedStatus() {
        return this.merged
    }

    /**
     * get tag name
     * 
     * @return string
     */
    def String getTag() {
        return this.tag
    }

    /**
     * check pull request and branch build status
     * 
     * @param script
     * @param check
     * @return
     */
    def checkStatus(script, check) {
        //GitHub check api payload format
        //{
        //  "state": "success",
        //  "target_url": "https://example.com/build/status",
        //  "description": "The build succeeded!",
        //  "context": "continuous-integration/jenkins"
        //}
        def checkName = this.getCheckName(check)
        def state = this.getState()
        def payload = JsonOutput.toJson([context: checkName, state: state, target_url: this.targetUrl, description: state])
        //GitHub check api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/statuses/${this.commitHash}"
        script.sh returnStatus: true, script: "set +e && curl -XPOST -H \"Content-Type: application/json\" -H \"Authorization: token ${this.accessToken}\" $endpoint -d '${payload}' && set -e"
    }

    /**
     * check branch build status
     * 
     * @param script
     * @param check
     * @return
     */
    def checkBranchStatus(script) {
        //GitHub check api payload format
        //{
        //  "state": "success",
        //  "target_url": "https://example.com/build/status",
        //  "description": "The build succeeded!",
        //  "context": "continuous-integration/jenkins/branch"
        //}
        def branchCheckName = CONTEXT_PREFIX + CONTEXT_BRANCH
        def payload = JsonOutput.toJson([context: branchCheckName, state: this.state, target_url: this.targetUrl, description: this.state])
        //GitHub check api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/statuses/${this.commitHash}"
        script.sh returnStatus: true, script: "set +e && curl -XPOST -H \"Content-Type: application/json\" -H \"Authorization: token ${this.accessToken}\" $endpoint -d '${payload}' && set -e"
    }

    /**
     * check pull request review
     * 
     * @param script
     * @param eventType
     * @return
     */
    def checkReview(script, eventType) {
        if (this.pullNumber.length() == 0) {
            return
        }
        def review = this.getReview(script)
        def event = this.getEventType(script, eventType)
        //GitHub pull request review api payload format
        //{
        //  "commit_id": "xxxxx",
        //  "body": "Please check unit test result.",
        //  "event": "COMMENT"
        //}
        def payload = JsonOutput.toJson([commit_id: this.commitHash, body: review, event: event])
        //github pull request review api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/pulls/${this.pullNumber}/reviews"
        script.sh returnStatus: true, script: "set +e && curl -XPOST -H \"Content-Type: application/json\" -H \"Authorization: token ${this.accessToken}\" $endpoint -d '${payload}' && set -e"
    }

    /**
     * get github account name
     * 
     * @return string
     */
    def String getAccountName() {
        return this.accountName
    }

    /**
     * set github account name
     * 
     * @param script
     */
    def setAccountName(script) {
        def userName = ''
        try {
            userName = script.env.GITHUB_CREDS_USR
        } catch (Exception e) {
            script.error "get account name error: ${e}"
            return
        }
        if (userName == null) {
            script.error 'get account name not found'
            return
        }
        userName = userName?.trim()
        if (userName.length() == 0) {
            script.error 'get account name empty'
            return
        }
        this.accountName = userName
    }

    /**
     * get github access token
     * 
     * @return string
     */
    def String getAccessToken() {
        return this.accessToken   
    }

    /**
     * set access token
     * 
     * @param script
     */
    def setAccessToken(script) {
        def token = ''
        try {
            token = script.env.GITHUB_CREDS_PSW
        } catch (Exception e) {
            script.error "get access token error: ${e}"
            return
        }
        if (token == null) {
            script.error 'get access token not found'
            return
        }
        token = token?.trim()
        if (token.length() == 0) {
            script.error 'get access token empty'
            return
        }
        this.accessToken = token
    }

    /**
     * get pull request review
     * 
     * @return string
     */
    def String getReview(script) {
        def reviewUrl = this.getTargetUrl()
        if (reviewUrl.length() > 0) {
            reviewUrl = "[Please check](${reviewUrl})"
        }
        return reviewUrl
    }

    /**
     * get github predefined event type
     * @param script
     * @param eventType
     * @return
     */
    def String getEventType(script, eventType) {
        eventType = eventType == null ? '' : eventType.trim()
        if (eventType.length() == 0) {
            script.error 'event type empty'
            return ''
        }
        //check github review event_type: APPROVE, REQUEST_CHANGES, COMMENT
        def eventTypeVal = EVENT_MAP[eventType]
        if (eventTypeVal == null) {
            script.error "event type ${eventType} undefined"
            return ''
        }
        if (eventTypeVal.length() == 0) {
            script.error "get event type empty: ${eventType}"
            return ''
        }
        return eventTypeVal
    }

    /**
     * get current state
     * 
     * @return String
     */
    def String getState() {
        return this.state
    }
    
    /**
     * set git check state by currentBuild.currentResult
     * 
     * @param script
     */
    def setState(script) {
        def stataVal = ''
        try {
            def result = script.currentBuild.currentResult
            if (result == null) {
                script.error 'get currentBuild.currentResult unset'
                return
            }
            stataVal = STATUS_MAP[result]
            if (stataVal == null) {
                script.error "get check state not found: ${result}"
                return
            }
            if (stataVal.length() == 0) {
                script.error "get check state empty: ${result}"
                return
            }
        } catch (Exception e) {
            script.error "get check state error: ${e}"
            return
        }
        this.state = stataVal
    }

    /**
     * generate full check name
     * 
     * @param name
     * @return string
     */
    def String getCheckName(name) {
        //create new git check name
        name = name?.trim()
        if (name.length() == 0) {
            return CONTEXT_PREFIX + CONTEXT_MERGE
        }
        return CONTEXT_PREFIX + name
    }

    /**
     * get git check target url
     * 
     * @return string
     */
    def String getTargetUrl() {
        return this.targetUrl
    }

    /**
     * set git check target url
     * 
     * @param script
     */
    def setTargetUrl(script) {
        def buildUrl = ''
        try {
            buildUrl = script.env.BUILD_URL
            if (buildUrl == null) {
                script.error 'check build url unset'
                return
            }
            buildUrl = buildUrl?.trim()
            if (buildUrl.length() == 0) {
                script.error 'check build url empty'
                return
            }
        } catch (Exception e) {
            script.error "get build url from env error: ${e}"
            return
        }
        //jenkins build console log detail page link with `console` suffix
        this.targetUrl = buildUrl + 'console'
    }

    /**
     * get git repository's owner and repo
     * 
     * @return Map
     */
    def Map getRepository() {
        return this.repository
    }

    /**
     * set git repository
     * 
     * @param script
     */
    def setRepository(script) {
        try {
            def parts = this.gitUrl.split('/').findAll { it -> it != '' }
            if (parts.size() < 4) {
                script.error "parse repo url invalid: ${this.gitUrl}"
                return
            }
            def owner = parts[2]
            def repo = parts[3]
            //git repo name splited by escaped "."
            def names = repo.split('\\.')
            if (names.size() != 2) {
                script.error "parse repo name ${repo} error"
                return
            }
            def repoName = names[0]
            repoName = repoName?.trim()
            if (repoName.length() == 0) {
                script.error "parse git repo name ${repo} empty"
                return
            }
            this.repository = [:]
            this.repository.putAt('owner', owner)
            this.repository.putAt('repo', repoName)
        } catch (Exception e) {
            script.error "get git repository error: ${e}"
            return
        }
    }

    /**
     * get pull request number
     *
     * @return string
     */
    def String getPullNumber() {
        return this.pullNumber
    }

    /**
     * set pull request number
     * 
     * @param script
     */
    def setPullNumber(script) {
        def prBranch = ''
        try {
            prBranch = script.env.GIT_BRANCH
        } catch (Exception e) {
            script.error "get pull number error: ${e}"
            return
        }
        if (prBranch == null || prBranch.length() == 0) {
            prBranch = script.env.BRANCH_NAME
        }
        if (prBranch == null || prBranch.length() == 0) {
            script.error "get pull request branch return null"
            return
        }
        prBranch = prBranch?.trim()
        if (prBranch.length() == 0) {
            script.error 'check git branch from env empty'
            return
        }
        def branches = prBranch.split('-').findAll { it -> it != '' }
        if (branches.size() == 2 && branches[0] == 'PR' && branches[1].isInteger()) {
            this.pullNumber = branches[1]
        }
    }

    /**
     * get git build pull request
     *
     * @return java.lang.Object
     */
    def getPR() {
        return this.prDetail
    }

    /**
     * get decoded branch detail
     * 
     * @return java.lang.Object
     */
    def getBranchDetail() {
        return this.branchDetail
    }

    /**
     * set branchDetail
     * 
     * @param script
     */
    def setBranchDetail(script) {
        //github branch api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/branches/${this.branch}"
        def branchContent = ''
        try {
            branchContent = script.sh returnStdout: true, script: "curl -H \"Authorization: token ${this.accessToken}\" $endpoint"
            branchContent = branchContent?.trim()
        } catch (Exception e) {
            script.error "get branch ${endpoint} error: ${e}"
            return
        }
        //branchContent = branchContent?.trim()
        if (branchContent.length() == 0) {
            script.error "check branch ${endpoint} empty"
            return
        }
        try {
            this.branchDetail = script.readJSON text: branchContent
        } catch (Exception e) {
            script.error "parse branch ${endpoint} exception: ${e}"
            return
        }
        if (this.branchDetail == null) {
            script.error "parse branch ${endpoint} null"
            return
        }
        def commitSha = ''
        try {
            commitSha = this.branchDetail.get('commit').get('sha').trim()
            if (commitSha.length() == 0) {
                script.error 'get commit sha from branch detail not found'
                return
            }
        } catch (Exception e) {
            script.error "get commit sha from branch detail error: ${e}"
            return
        }
        this.commitHash = commitSha
        this.tag = this.branch + '_' + this.buildNumber + '_' + this.commitHash.take(GITHUB_TAG_LENGTH)
    }

    /**
     * get real commit id by pull number
     * <p>
     * Jenkins will change commit id to temporary inner commit id
     * </p>
     * 
     * @param script
     * @param parsedPr
     * @return string
     */
    def String getCommitId(script, parsedPr) {
        def prCommitId = ''
        try {
            prCommitId = parsedPr.get('head').get('sha').trim()
            if (prCommitId.length() == 0) {
                script.error 'parse pull request commit id empty'
                return ''
            }
        } catch (Exception e) {
            script.error "get commit sha from pull reqest object error: ${e}"
            return ''
        }
        return prCommitId
    }

    /**
     * get git url from env
     *
     * @return string
     */
    def String getGitUrl() {
        return this.gitUrl
    }

    /**
     * set git url
     * 
     * @param script
     */
    def setGitUrl(script) {
        def gitURL = ''
        try {
            gitURL = script.env.GIT_URL
        } catch (Exception e) {
            script.error "get git url error: ${e}"
            return
        }
        if (gitURL == null) {
            script.error 'get git url not found'
            return
        }
        gitURL = gitURL?.trim()
        if (gitURL.length() == 0) {
            script.error 'git url empty'
            return
        }
        if (!gitURL.endsWith('.git')) {
            script.error "git url invalid: ${gitURL}"
            return
        }
        this.gitUrl = gitURL
    }

    /**
     * get git branch
     *
     * @return string
     */
    def String getBranch() {
        return this.branch
    }

    /**
     * set git branch
     * 
     * @param script
     */
    def setBranch(script) {
        def branchVal = ''
        if (this.pullNumber.length() > 0) {
            //build triggered by pull request
            branchVal = script.env.CHANGE_BRANCH
        } else {
            //build triggered by branch
            branchVal = script.env.GIT_BRANCH
            branchVal = branch?.trim()
            if (branchVal.length() == 0) {
                branchVal = script.env.BRANCH_NAME
            }
        }
        branchVal = branchVal?.trim()
        //check branch empty
        if (branchVal.length() == 0) {
            script.error 'git branch parse empty'
            return
        }
        //warning: Jenkins may create temporary branch with prefix 'PR'
        def gitbranches = branchVal.split('-')
        //check branch name PR-{n} which generated by Jenkins
        if (gitbranches.size() == 2 && gitbranches[0] == 'PR') {
            script.error "get branch error: ${branchVal}"
            return
        }
        this.branch = branchVal
    }
    
    /**
     * get pull request id from commit message
     * 
     * @param script
     * @param decodeBranch
     * @return string
     */
    def String getCommitPullNumber(script) {
        def commitMsg = ''
        try {
            commitMsg = this.branchDetail.get('commit').get('commit').get('message').trim()
        } catch (Exception e) {
            script.error "get latest commit message error: ${e}"
            return ''
        }
        if (commitMsg.length() == 0) {
            script.error 'get branch commit message not found'
            return ''
        }
        //parse pull request from latest commit message
        //e.g. #32
        def matched = (commitMsg =~ /#\d+/).findAll()
        //e.g. ['#32']
        if (matched == null || matched.size() != 1) {
            return ''
        }
        def commitPrId = matched[0]
        if (commitPrId[0] != '#') {
            return ''
        }
        def prId = commitPrId.minus('#')
        if (!prId.isInteger()) {
            script.error "[parse pull request id] parse pull request id ${prId}"
            return ''
        }
        return prId
    }

    /**
     * get build number
     *
     * @return string
     */
    def String getBuildNumber() {
        return this.buildNumber
    }

    /**
     * set build number
     * 
     * @param script
     */
    def setBuildNumber(script) {
        def buildNum = ''
        try {
            buildNum = script.env.BUILD_NUMBER
            if (!buildNum.isInteger()) {
                script.error "get build number error: ${buildNum}"
                return
            }
        } catch (Exception e) {
            script.error "get build number exception: ${e}"
            return
        }
        this.buildNumber = buildNum
    }

    /**
     * create git tag
     * 
     * @param script
     * @return string
     */
    def String createTag(script) {
        def parseTag = this.createTagObject(script)
        if (parseTag == null) {
            script.error "create tag object return null"
            return ''
        }
        def refSha = parseTag.get('sha')
        if (refSha == null) {
            script.error "parse tag ref return null"
            return ''
        }
        refSha = refSha?.trim()
        if (refSha.length() == 0) {
            script.error "parse tag ref empty"
            return ''
        }
        return this.createTagReference(script, this.tag, refSha)
    }

    /**
     * create github tag
     * 
     * @param script
     * @return java.lang.Object
     */
    def createTagObject(script) {
        //GitHub tag object api payload format
        //{
        //  "tag": "v0.0.1",
        //  "message": "initial version\n",
        //  "object": "xxxxx",
        //  "type": "commit",
        //  "tagger": {
        //    "name": "Monalisa Octocat",
        //    "email": "octocat@github.com",
        //    "date": "2011-06-17T14:53:35-07:00"
        //  }
        //}
        def currentTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
        def tagger = [name: this.accountName, email: SERVICE_ACCOUNT, date: currentTime]
        def payload = JsonOutput.toJson([tag: this.tag, message: this.tagMessage, object: this.commitHash, type: TAG_COMMIT, tagger: tagger])
        //github tag api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/git/tags"
        def tagContent = ''
        try {
            def tagRes = script.httpRequest contentType: 'APPLICATION_JSON', httpMode: 'POST', ignoreSslErrors: true, requestBody: payload, customHeaders: [[maskValue: true, name: 'Authorization', value: "token ${this.accessToken}"]], url: endpoint, wrapAsMultipart: false
            if (tagRes.getStatus() != 201) {
                script.error "create tag object ${endpoint} failed"
                return null
            }
            tagContent = tagRes.getContent()
        } catch (Exception e) {
            script.error "create tag object ${endpoint} error: ${e}"
            return null
        }
        if (tagContent == null) {
            script.error "get tag object ${endpoint} response return null"
            return null
        }
        tagContent = tagContent?.trim()
        if (tagContent.length() == 0) {
            script.error "parse tag object ${endpoint} response empty"
            return null
        }
        def tagObject = null
        try {
            tagObject = script.readJSON text: tagContent
        } catch (Exception e) {
            script.error "parse tag object ${endpoint} response errror: ${e}"
            return null
        }
        if (tagObject == null) {
            script.error "parse tag object ${endpoint} response null"
            return null
        }
        return tagObject
    }

    /**
     * create tag reference
     * 
     * @param script
     * @return string
     */
    def String createTagReference(script, tagVal, shaVal) {
        //GitHub reference object api payload format
        //{
        //  "ref": "refs/tags/tagA",
        //  "sha": "xxx"
        //}
        def refVal = 'refs/tags/' + tagVal
        def payload = JsonOutput.toJson(['ref': refVal, 'sha': shaVal])
        //github reference api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/git/refs"
        try {
            def refResponse = script.sh returnStatus: true, script: "set +e && curl -XPOST -H \"Content-Type: application/json\" -H \"Authorization: token ${this.accessToken}\" $endpoint -d '${payload}' && set -e"
            if (refResponse == null) {
                script.error "parse tag reference response null"
                return ''
            }
        } catch (Exception e) {
            return ''
        }
        return tagVal
    }

    /**
     * set build tag name
     */
    def generateTag() {
        this.tag = this.branch + '_' + this.buildNumber + '_' + this.commitHash.take(GITHUB_TAG_LENGTH)
        script.echo "############# git tag: ############# --------- ${this.tag}"
    }

    /**
     * set tag
     */
    def setTag(tagName) {
        this.tag = tagName
    }

    /**
     * get tag message
     * 
     * @return string
     */
    def String getTagMessage() {
        return this.tagMessage
    }

    /**
     * set tag message
     * 
     * @param script
     */
    def setTagMessage(script) {
        if (this.pullNumber.length() > 0) {
            return "Triggered by PR: #${this.pullNumber}"
        }
        return "Triggered by branch: ${this.branch}"
    }

    /**
     * check pull request merged status
     * 
     * @param script
     */
    def checkPullRequest(script) {
        if (this.pullNumber.length() == 0) {
            //commit message example:
            //{
            //  "message":"Merge pull request #30 from cannal: create unit test branch"
            //}
            this.pullNumber = this.getCommitPullNumber(script)
        }
        if (this.pullNumber.length() == 0) {
            script.error "check pull request not found"
            return
        }
        //github pull request api endpoint
        def endpoint = "${GITHUB_API}/repos/${this.repository['owner']}/${this.repository['repo']}/pulls/${this.pullNumber}"
        def prContent = ''
        try {
            prContent = script.sh returnStdout: true, script: "curl -H \"Content-Type: application/json\" -H \"Authorization: token ${this.accessToken}\" $endpoint"
            prContent = prContent?.trim()
        } catch (Exception e) {
            script.error "get pull request ${endpoint} error: ${e}"
            return
        }
        //prContent = prContent?.trim()
        if (prContent.length() == 0) {
            script.error "check pr ${endpoint} empty"
            return
        }
        try {
            this.prDetail = script.readJSON text: prContent
        } catch (Exception e) {
            script.error "parse pr ${endpoint} exception: ${e}"
            return
        }
        if (this.prDetail == null) {
            script.error "parse pull request detail return null"
            return
        }
        //pipeline triggered by branch with merged pull request
        //get pull request from target branch commits
        //explain:
        // only the branch that merged with specified pull request is permitted to trigger build step
        //commit created by pull request
        def mergeCommitSha = ''
        try {
            mergeCommitSha = this.prDetail.get('merge_commit_sha').trim()
        } catch (Exception e) {
            script.error "get commit sha from pull request detail error: ${e}"
            return
        }
        if (mergeCommitSha.length() == 0) {
            script.error "get commit sha from pull request not found"
            return
        }
        //pull request merge to base branch
        def baseBranch = ''
        try {
            baseBranch = this.prDetail.get('base').get('ref').trim()
        } catch (Exception e) {
            script.error "get base branch from pull request detail error: ${e}"
            return
        }
        if (baseBranch.length() == 0) {
            script.error "get base branch from pull request not found"
            return
        }
        //pull request state(e.g. open, closed)
        def mergeState = ''
        try {
            mergeState = this.prDetail.get('state').trim()
        } catch (Exception e) {
            script.error "get pull request state error: ${e}"
            return
        }
        if (mergeState.length() == 0) {
            script.error "get pull request state not found"
            return
        }
        //pull request locked status
        def locked = true
        try {
            locked = this.prDetail.get('locked')
        } catch (Exception e) {
            script.error "get pull request locked status error: ${e}"
            return
        }
        //check pull request locked status
        if (locked) {
            script.error "pull request status is locked"
            return
        }
        //check pull request merge state:
        //1. merge commit to target branch
        //2. close pull request
        if (mergeState != PULL_REQUEST_CLOSED) {
            return
        }
        //check pull request commit hash with branch commit id
        if (this.commitHash != mergeCommitSha) {
            script.echo "branch commit ${commitVal} mismatch with pull request commit: ${mergeCommitSha}"
            return
        }
        //merged status is used to control docker image build/push and git tag create
        this.merged = true
        //set tag
        this.generateTag()
    }

    /**
     * get unit test coverage
     * 
     * @param script
     */
    def String getUnitTestCoverage(script) {
        def cov = script.sh returnStdout: true, script: "go tool cover -func coverage.cov | grep total | awk '{print substr(\$3, 1, length(\$3)-1)}'"
        if (cov == null) {
            script.error "parse unit coverage return null"
            return ''
        }
        //e.g. '78.2'
        return cov
    }
}

