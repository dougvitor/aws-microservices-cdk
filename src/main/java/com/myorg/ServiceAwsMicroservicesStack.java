package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

public class ServiceAwsMicroservicesStack extends Stack {
    public ServiceAwsMicroservicesStack(final Construct scope, final String id, final Cluster cluster) {
        this(scope, id, null, cluster);
    }

    public ServiceAwsMicroservicesStack(final Construct scope, final String id, final StackProps props, final Cluster cluster) {
        super(scope, id, props);

        final ApplicationLoadBalancedFargateService albServiceAwsMicroservicesFargate = createServiceAwsMicroservicesApplicationLoadBalancerFargate(cluster);

        createTargetGroupAwsMicroservices(albServiceAwsMicroservicesFargate);

        final ScalableTaskCount scalableTaskCount = createInstanceCapacityScaling(albServiceAwsMicroservicesFargate);

        createCpuUtilizationScaling(scalableTaskCount);


    }

    private ApplicationLoadBalancedFargateService createServiceAwsMicroservicesApplicationLoadBalancerFargate(Cluster cluster) {
        return ApplicationLoadBalancedFargateService
                .Builder
                .create(this, "ALB_AWS_MICROSERVICES")
                .serviceName("serviceAwsMicroservices")
                .cluster(cluster)
                .cpu(512)
                .desiredCount(2)
                .listenerPort(8080)
                .assignPublicIp(true)
                .memoryLimitMiB(1024)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions
                                .builder()
                                .containerName("aws_microservices")
                                .image(ContainerImage.fromRegistry("dougiesvitor/aws-microservice:1.1.0"))
                                .containerPort(8080)
                                .logDriver(
                                        LogDriver.awsLogs(
                                                AwsLogDriverProps
                                                        .builder()
                                                        .logGroup(
                                                                LogGroup
                                                                        .Builder
                                                                        .create(this, "ServiceAwsMicroservicesGroup")
                                                                        .logGroupName("ServiceAwsMicroservicesLogGroup")
                                                                        .removalPolicy(RemovalPolicy.DESTROY)
                                                                        .build()
                                                        )
                                                        .streamPrefix("ServiceAwsMicroservicesLog")
                                                        .build()
                                        )
                                )
                                .build()
                )
                .publicLoadBalancer(true)
                .build();
    }

    private void createTargetGroupAwsMicroservices(ApplicationLoadBalancedFargateService albServiceAwsMicroservicesFargate) {
        albServiceAwsMicroservicesFargate
                .getTargetGroup()
                .configureHealthCheck(
                        new HealthCheck
                                .Builder()
                                .path("/actuator/health")
                                .port("8080")
                                .healthyHttpCodes("200")
                                .build()
                );
    }

    private ScalableTaskCount createInstanceCapacityScaling(ApplicationLoadBalancedFargateService albServiceAwsMicroservicesFargate) {
        return albServiceAwsMicroservicesFargate
                .getService()
                .autoScaleTaskCount(
                        EnableScalingProps
                                .builder()
                                .minCapacity(2)
                                .maxCapacity(4)
                                .build()
                );
    }

    private void createCpuUtilizationScaling(ScalableTaskCount scalableTaskCount) {
        scalableTaskCount
                .scaleOnCpuUtilization(
                        "ServiceAwsMicroservicesAutoScaling",
                        CpuUtilizationScalingProps
                                .builder()
                                .targetUtilizationPercent(50)
                                .scaleInCooldown(Duration.seconds(60))
                                .scaleOutCooldown(Duration.seconds(60))
                                .build()
                );
    }
}
