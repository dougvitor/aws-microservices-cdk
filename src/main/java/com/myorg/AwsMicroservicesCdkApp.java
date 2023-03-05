package com.myorg;

import software.amazon.awscdk.App;

public class AwsMicroservicesCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        final VpcStack vpcStack = new VpcStack(app, "Vpc");

        final ClusterStack clusterStack = new ClusterStack(app, "Cluster", vpcStack.getVpc());
        clusterStack.addDependency(vpcStack);

        final RdsStack rdsStack = new RdsStack(app, "Rds", vpcStack.getVpc());
        rdsStack.addDependency(vpcStack);

        SnsStack snsStack = new SnsStack(app, "Sns");

        final ServiceAwsMicroservicesStack serviceAwsMicroservicesStack = new ServiceAwsMicroservicesStack(app,
                "ServiceAwsMicroservices",
                clusterStack.getCluster(),
                snsStack.getProductEventsTopic());
        serviceAwsMicroservicesStack.addDependency(clusterStack);
        serviceAwsMicroservicesStack.addDependency(rdsStack);
        serviceAwsMicroservicesStack.addDependency(snsStack);

        final DynamodbStack dDbStack = new DynamodbStack(app, "Ddb");

        final ServiceAwsMicroservicesConsumerStack serviceAwsMicroservicesConsumerStack = new ServiceAwsMicroservicesConsumerStack(app,
                "ServiceAwsMicroservicesConsumer",
                clusterStack.getCluster(),
                snsStack.getProductEventsTopic(),
                dDbStack.getProductEventsDdb()
        );
        serviceAwsMicroservicesConsumerStack.addDependency(clusterStack);
        serviceAwsMicroservicesConsumerStack.addDependency(snsStack);
        serviceAwsMicroservicesConsumerStack.addDependency(dDbStack);

        app.synth();
    }
}

