// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def infrastructureRepository = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/doa17-infrastructure"

// Views
def doa17EnvironmentPipeline = buildPipelineView(projectFolderName + "/DOA17_Environment_Pipeline")

// Jobs DOA17_Environment_Pipeline
def doa17LaunchEnvironment = freeStyleJob(projectFolderName + "/DOA17_Launch_Environment")
def doa17CreateApplication = freeStyleJob(projectFolderName + "/DOA17_Create_Application")
def doa17CreateDevelopmentGroup = freeStyleJob(projectFolderName + "/DOA17_Create_Development_Group")
def doa17CreateProductionGroup = freeStyleJob(projectFolderName + "/DOA17_Create_Production_Group")

// DOA17_Environment_Pipeline
doa17EnvironmentPipeline.with{
    title('DOA17 Environment Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/DOA17_Launch_Environment")
    showPipelineDefinitionHeader()
    alwaysAllowManualTrigger()
    refreshFrequency(5)
}

// Job DOA17_Launch_Environment
doa17LaunchEnvironment.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
  }
  parameters{
    stringParam("AWS_REGION",'us-east-1',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("WEB_APP_PROFILE",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
    stringParam("WEB_APP_SG",'',"Web App SG from DevOps-Workshop-Networking stack")
    stringParam("PUBLIC_SUBNET",'',"Public Subnet from DevOps-Workshop-Networking stack")
    stringParam("CODE_DEPLOY_ARN",'',"IAM Role ARN from DevopsWorkshop-raem-roles stack")
  }
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
   scm{
    git{
      remote{
        url(infrastructureRepository)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
    steps {
    shell('''
set +x

export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Creating DevopsWorkshop-${ENVIRONMENT_NAME} Stack"
aws cloudformation create-stack --stack-name DevopsWorkshop-${ENVIRONMENT_NAME} --template-body file:///${WORKSPACE}/03-aws-devops-workshop-environment-setup.template --capabilities CAPABILITY_IAM \
--parameters  ParameterKey=EnvironmentName,ParameterValue=$ENVIRONMENT_NAME \
              ParameterKey=WebAppInstanceProfile,ParameterValue=$WEB_APP_PROFILE \
              ParameterKey=WebAppSG,ParameterValue=$WEB_APP_SG \
              ParameterKey=publicSubnet01,ParameterValue=$PUBLIC_SUBNET

echo "[INFO] Wating for DevopsWorkshop-${ENVIRONMENT_NAME} Stack"
aws cloudformation wait stack-create-complete --stack-name DevopsWorkshop-${ENVIRONMENT_NAME}
echo "[INFO] DevopsWorkshop-${ENVIRONMENT_NAME} Stack Created"

echo "[INFO] Creating Code Build Project"
aws codebuild create-project --cli-input-json file://${WORKSPACE}/create-project.json



set -x'''.stripMargin()
    )
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/DOA17_Create_Application"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          currentBuild()
        }
      }
    }
  }
}

// Job DOA17_Create_Application
doa17CreateApplication.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
	
  }
  parameters{
    stringParam("AWS_REGION",'',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("S3_BUCKET",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
    stringParam("WEB_APP_PROFILE",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
    stringParam("WEB_APP_SG",'',"Web App SG from DevOps-Workshop-Networking stack")
    stringParam("PUBLIC_SUBNET",'',"Public Subnet from DevOps-Workshop-Networking stack")
    stringParam("CODE_DEPLOY_ARN",'',"IAM Role ARN from DevopsWorkshop-raem-roles stack")
  }
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
    steps {
    shell('''
set +x
export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Creating Code Deploy Application ${ENVIRONMENT_NAME}-WebApp"
aws deploy create-application --application-name ${ENVIRONMENT_NAME}-WebApp
set -x'''.stripMargin()
    )
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/DOA17_Create_Development_Group"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          currentBuild()
        }
      }
    }
  }
}

// Job DOA17_Create_Development_Group
doa17CreateDevelopmentGroup.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
  }
  parameters{
    stringParam("AWS_REGION",'',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("S3_BUCKET",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
    stringParam("WEB_APP_PROFILE",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
    stringParam("WEB_APP_SG",'',"Web App SG from DevOps-Workshop-Networking stack")
    stringParam("PUBLIC_SUBNET",'',"Public Subnet from DevOps-Workshop-Networking stack")
    stringParam("CODE_DEPLOY_ARN",'',"IAM Role ARN from DevopsWorkshop-raem-roles stack")
}
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
    steps {
    shell('''
set +x
export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Creating Code Deploy Deployment Group ${ENVIRONMENT_NAME}-DevWebApp"
aws deploy create-deployment-group --application-name ${ENVIRONMENT_NAME}-WebApp  --deployment-config-name CodeDeployDefault.OneAtATime --deployment-group-name ${ENVIRONMENT_NAME}-DevWebApp --ec2-tag-filters Key=Name,Value=${ENVIRONMENT_NAME}-DevWebApp,Type=KEY_AND_VALUE --service-role-arn ${CODE_DEPLOY_ARN}

set -x'''.stripMargin()
    )
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/DOA17_Create_Production_Group"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          currentBuild()
        }
      }
    }
  }
}

// Job DOA17_Create_Production_Group
doa17CreateProductionGroup.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
  }
  parameters{
    stringParam("AWS_REGION",'',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("S3_BUCKET",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
       stringParam("WEB_APP_PROFILE",'',"Web App Instance Profile from DevOps-Workshop-Networking stack")
    stringParam("WEB_APP_SG",'',"Web App SG from DevOps-Workshop-Networking stack")
    stringParam("PUBLIC_SUBNET",'',"Public Subnet from DevOps-Workshop-Networking stack")
    stringParam("CODE_DEPLOY_ARN",'',"IAM Role ARN from DevopsWorkshop-raem-roles stack")
}
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
    steps {
    shell('''
set +x
export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Creating Code Deploy Deployment Group ${ENVIRONMENT_NAME}-ProdWebApp"
aws deploy create-deployment-group --application-name ${ENVIRONMENT_NAME}-WebApp  --deployment-config-name CodeDeployDefault.OneAtATime --deployment-group-name ${ENVIRONMENT_NAME}-ProdWebApp --ec2-tag-filters Key=Name,Value=${ENVIRONMENT_NAME}-ProdWebApp,Type=KEY_AND_VALUE --service-role-arn ${CODE_DEPLOY_ARN}


set -x'''.stripMargin()
    )
  }
}
