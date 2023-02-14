package com.myorg;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class ServiceAwsMicroservicesConsumerStack extends Stack {
    public ServiceAwsMicroservicesConsumerStack(final Construct scope, final String id, final Cluster cluster, final SnsTopic productEventsTopic) {
        this(scope, id, null, cluster, productEventsTopic);
    }

    public ServiceAwsMicroservicesConsumerStack(final Construct scope, final String id, final StackProps props, final Cluster cluster, final SnsTopic productEventsTopic) {
        super(scope, id, props);

        Queue productEventsQueue = getQueue(productEventsTopic);

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("AWS_REGION", "us-east-1");
        envVariables.put("AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME", productEventsQueue.getQueueName());

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

    private Queue getQueue(SnsTopic productEventsTopic) {
        Queue productEventsDlq = Queue.Builder.create(this, "ProductEventsDlq")
                .queueName("product-events-dlq")
                .build();

        DeadLetterQueue deadLetterQueue = DeadLetterQueue.builder()
                .queue(productEventsDlq)
                .maxReceiveCount(3)
                .build();

        Queue productEventsQueue = Queue.Builder.create(this, "ProductEvents")
                .queueName("product-events")
                .deadLetterQueue(deadLetterQueue)
                .build();

        SqsSubscription sqsSubscription = SqsSubscription.Builder.create(productEventsQueue).build();
        productEventsTopic.getTopic().addSubscription(sqsSubscription);
        return productEventsQueue;
    }
}
