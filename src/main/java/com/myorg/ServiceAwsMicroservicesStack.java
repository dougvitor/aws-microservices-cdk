package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class ServiceAwsMicroservicesStack extends Stack {
    public ServiceAwsMicroservicesStack(final Construct scope, final String id, final Cluster cluster, final SnsTopic productEventsTopic) {
        this(scope, id, null, cluster, productEventsTopic);
    }

    public ServiceAwsMicroservicesStack(final Construct scope, final String id, final StackProps props, final Cluster cluster, final SnsTopic productEventsTopic) {
        super(scope, id, props);

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("SPRING_DATASOURCE_URL",
                "jdbc:mariadb://" + Fn.importValue("rds-endpoint") + ":3306/aws-microservices?createDatabaseIfNotExist=true");
        envVariables.put("SPRING_DATASOURCE_USERNAME", "admin");
        envVariables.put("SPRING_DATASOURCE_PASSWORD", Fn.importValue("rds-password"));
        envVariables.put("AWS_REGION", "us-east-1");
        envVariables.put("AWS_SNS_TOPIC_PRODUCT_EVENTS_ARN", productEventsTopic.getTopic().getTopicArn());

        final ApplicationLoadBalancedFargateService albServiceAwsMicroservicesFargate = createServiceAwsMicroservicesApplicationLoadBalancerFargate(cluster, envVariables);

        configureHealthCheckTargetGroupAwsMicroservices(albServiceAwsMicroservicesFargate);

        final ScalableTaskCount scalableTaskCount = createInstanceCapacityScaling(albServiceAwsMicroservicesFargate);

        configureCpuUtilizationScaling(scalableTaskCount);

        productEventsTopic.getTopic().grantPublish(albServiceAwsMicroservicesFargate.getTaskDefinition().getTaskRole());

    }

    private ApplicationLoadBalancedFargateService createServiceAwsMicroservicesApplicationLoadBalancerFargate(final Cluster cluster, final Map<String, String> envVariables) {
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
                                .image(ContainerImage.fromRegistry("dougiesvitor/aws-microservice:1.5.0"))
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
                                .environment(envVariables)
                                .build()
                )
                .publicLoadBalancer(true)
                .build();
    }

    private void configureHealthCheckTargetGroupAwsMicroservices(ApplicationLoadBalancedFargateService albServiceAwsMicroservicesFargate) {
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

    private void configureCpuUtilizationScaling(ScalableTaskCount scalableTaskCount) {
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
