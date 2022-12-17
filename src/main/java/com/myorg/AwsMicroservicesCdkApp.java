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

        final ServiceAwsMicroservicesStack serviceAwsMicroservicesStack = new ServiceAwsMicroservicesStack(app, "ServiceAwsMicroservices", clusterStack.getCluster());
        serviceAwsMicroservicesStack.addDependency(clusterStack);
        serviceAwsMicroservicesStack.addDependency(rdsStack);

        app.synth();
    }
}

