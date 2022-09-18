package com.myorg;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

public class ServiceAwsMicroservicesStack extends Stack {
    public ServiceAwsMicroservicesStack(final Construct scope, final String id, final Cluster cluster) {
        this(scope, id, null, cluster);
    }

    public ServiceAwsMicroservicesStack(final Construct scope, final String id, final StackProps props, final Cluster cluster) {
        super(scope, id, props);

        final ApplicationLoadBalancedFargateService serviceAwsMicroservicesFargate = ApplicationLoadBalancedFargateService
                .Builder
                .create(this, "ALB_AWS_MICROSERVICES")
                .cluster(cluster)
                .cpu(512)
                .desiredCount(2)
                .listenerPort(8080)
                .memoryLimitMiB(1024)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions
                                .builder()
                                .containerName("aws_microservices")
                                .image(ContainerImage.fromRegistry("dougiesvitor/aws-microservice:1.0.0"))
                                .containerPort(8080)
                                .logDriver(
                                        LogDriver.awsLogs(
                                                AwsLogDriverProps
                                                        .builder()
                                                        .logGroup(
                                                                LogGroup
                                                                        .Builder
                                                                        .create(this, "ServiceAwsMicroservicesGroup")
                                                                        .logGroupName("ServiceAwsMicroservices")
                                                                        .removalPolicy(RemovalPolicy.DESTROY)
                                                                        .build()
                                                        )
                                                        .streamPrefix("ServiceAwsMicroservices")
                                                        .build()
                                        )
                                )
                                .build()
                )
                .publicLoadBalancer(true)
                .build();
    }
}
