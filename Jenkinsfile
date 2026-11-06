// Import this pipeline into a dedicated TPS job after provisioning a Windows/JDK21/Node24 agent.
// It builds one TPS application, and does not restart a deployed site.
pipeline {
    agent { label 'tps-windows' }
    options { disableConcurrentBuilds(); timestamps() }
    parameters {
        choice(name: 'BUILD_TOOL', choices: ['Maven', 'Gradle'], description: 'Same TPS sources; Maven is the primary build.')
        booleanParam(name: 'PUBLISH_NEXUS', defaultValue: false, description: 'Publish only after configuring a TPS-specific hosted Maven repository and credentials.')
        string(name: 'NEXUS_REPOSITORY_URL', defaultValue: '', description: 'Full HTTPS hosted repository URL; not the Nexus home page.')
    }
    stages {
        stage('Checkout') { steps { checkout scm } }
        stage('Build and unit tests') {
            steps { powershell '& ./scripts/build.ps1 -BuildTool $env:BUILD_TOOL' }
            post { always { junit allowEmptyResults: true, testResults: 'server/target/surefire-reports/*.xml,server/build/test-results/test/*.xml' } }
        }
        stage('Publish to Nexus') {
            when { expression { params.PUBLISH_NEXUS } }
            steps {
                powershell '''
                    $ErrorActionPreference='Stop'
                    $u=[uri]$env:NEXUS_REPOSITORY_URL
                    if($u.Scheme -ne 'https' -or $u.DnsSafeHost -ne 'nexus.ls-thirautech.com' -or $u.Port -ne 443 -or $u.UserInfo -or $u.Query -or $u.Fragment){throw 'Only the approved company Nexus HTTPS endpoint is allowed.'}
                    # Set this in trusted Jenkins job/agent configuration, never as a build parameter.
                    if($env:TPS_NEXUS_ALLOWED_REPOSITORY -notmatch '^[A-Za-z0-9._-]+$'){throw 'Configure TPS_NEXUS_ALLOWED_REPOSITORY in trusted job configuration.'}
                    if($u.AbsolutePath.TrimEnd('/') -cne ('/repository/'+$env:TPS_NEXUS_ALLOWED_REPOSITORY)){throw 'Repository is not approved for this job.'}
                '''
                withCredentials([usernamePassword(credentialsId: 'tps-nexus-publisher', usernameVariable: 'TPS_NEXUS_USER', passwordVariable: 'TPS_NEXUS_PASSWORD')]) {
                    powershell '''
                        $ErrorActionPreference='Stop'
                        $env:TPS_NEXUS_REPOSITORY_URL=$env:NEXUS_REPOSITORY_URL
                        if($env:BUILD_TOOL -eq 'Maven') {
                            & ./mvnw.cmd -B -s ci/maven-settings.xml -f server/pom.xml deploy "-DaltDeploymentRepository=tps-nexus::$env:TPS_NEXUS_REPOSITORY_URL"
                        } else {
                            & ./gradlew.bat --no-daemon :server:publishTpsPublicationToTpsNexusRepository
                        }
                        if($LASTEXITCODE -ne 0){throw 'Nexus publication failed'}
                    '''
                }
            }
        }
    }
    post { success { archiveArtifacts artifacts: 'dist/**/*', fingerprint: true } }
}
