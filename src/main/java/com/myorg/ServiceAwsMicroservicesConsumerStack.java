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

import java.util.HashMap;
import java.util.Map;

public class ServiceAwsMicroservicesConsumerStack extends Stack {
    public ServiceAwsMicroservicesConsumerStack(final Construct scope, final String id, final Cluster cluster) {
        this(scope, id, null, cluster);
    }

    public ServiceAwsMicroservicesConsumerStack(final Construct scope, final String id, final StackProps props, final Cluster cluster) {
        super(scope, id, props);

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("AWS_REGION", "us-east-1");

        final ApplicationLoadBalancedFargateService albServiceAwsMicroservicesConsumerFargate = createServiceAwsMicroservicesApplicationLoadBalancerFargate(cluster, envVariables);

        configureHealthCheckTargetGroupAwsMicroservices(albServiceAwsMicroservicesConsumerFargate);

        final ScalableTaskCount scalableTaskCount = createInstanceCapacityScaling(albServiceAwsMicroservicesConsumerFargate);

        configureCpuUtilizationScaling(scalableTaskCount);
    }

    private ApplicationLoadBalancedFargateService createServiceAwsMicroservicesApplicationLoadBalancerFargate(final Cluster cluster, final Map<String, String> envVariables) {
        return ApplicationLoadBalancedFargateService
                .Builder
                .create(this, "ALB_AWS_MICROSERVICES_CONSUMER")
                .serviceName("serviceAwsMicroservicesConsumer")
                .cluster(cluster)
                .cpu(512)
                .desiredCount(2)
                .listenerPort(9090)
                .assignPublicIp(true)
                .memoryLimitMiB(1024)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions
                                .builder()
                                .containerName("aws_microservices_consumer")
                                .image(ContainerImage.fromRegistry("dougiesvitor/aws-microservice-consumer:1.0.0"))
                                .containerPort(9090)
                                .logDriver(
                                        LogDriver.awsLogs(
                                                AwsLogDriverProps
                                                        .builder()
                                                        .logGroup(
                                                                LogGroup
                                                                        .Builder
                                                                        .create(this, "ServiceAwsMicroservicesConsumerGroup")
                                                                        .logGroupName("ServiceAwsMicroservicesConsumerLogGroup")
                                                                        .removalPolicy(RemovalPolicy.DESTROY)
                                                                        .build()
                                                        )
                                                        .streamPrefix("ServiceAwsMicroservicesConsumerLog")
                                                        .build()
                                        )
                                )
                                .environment(envVariables)
                                .build()
                )
                .publicLoadBalancer(true)
                .build();
    }

    private void configureHealthCheckTargetGroupAwsMicroservices(ApplicationLoadBalancedFargateService albServiceAwsMicroservicesConsumerFargate) {
        albServiceAwsMicroservicesConsumerFargate
                .getTargetGroup()
                .configureHealthCheck(
                        new HealthCheck
                                .Builder()
                                .path("/actuator/health")
                                .port("9090")
                                .healthyHttpCodes("200")
                                .build()
                );
    }

    private ScalableTaskCount createInstanceCapacityScaling(ApplicationLoadBalancedFargateService albServiceAwsMicroservicesConsumerFargate) {
        return albServiceAwsMicroservicesConsumerFargate
                .getService()
                .autoScaleTaskCount(
                        EnableScalingProps
                                .builder()
                                .minCapacity(2)
                                .maxCapacity(4)
                                .build()
                );
    }

    private void configureCpuUtilizationScaling(ScalableTaskCount scalableTaskCount) {
        scalableTaskCount
                .scaleOnCpuUtilization(
                        "ServiceAwsMicroservicesConsumerAutoScaling",
                        CpuUtilizationScalingProps
                                .builder()
                                .targetUtilizationPercent(50)
                                .scaleInCooldown(Duration.seconds(60))
                                .scaleOutCooldown(Duration.seconds(60))
                                .build()
                );
    }
}
