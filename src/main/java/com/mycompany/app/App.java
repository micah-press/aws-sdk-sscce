package com.mycompany.app;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import software.amazon.awssdk.auth.credentials.AwsCredentials;

public class App {
    protected static class AwsCredentialsShim implements AWSCredentials {
        private String accessKey;
        private String secretKey;

        public AwsCredentialsShim(String accessKey, String secretKey) {
            this.accessKey = accessKey;
            this.secretKey = secretKey;
        }

        @Override
        public String getAWSAccessKeyId() {
            return accessKey;
        }

        @Override
        public String getAWSSecretKey() {
            return secretKey;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("Access Key: ")
                    .append(accessKey + "\n")
                    .append("Secret Key: ")
                    .append(secretKey)
                    .toString();
        }
    }

    /**
     * Uses a {@link software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider} to implement
     * {@link com.amazonaws.auth.AWSCredentialsProvider}.
     */
    protected static class V2ProfileCredentialsProvider implements AWSCredentialsProvider {
        private final software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider provider;
        private AWSCredentials creds;

        public V2ProfileCredentialsProvider() {
            provider = software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider.create();
            setCredentials();
        }

        private void setCredentials() {
            AwsCredentials v2CredentialsObject = provider.resolveCredentials();
            creds = new AwsCredentialsShim(v2CredentialsObject.accessKeyId(), v2CredentialsObject.secretAccessKey());
        }

        @Override
        public AWSCredentials getCredentials() {
            return creds;
        }

        @Override
        public void refresh() {
            setCredentials();
        }
    }

    /**
     * Implementation copied almost exactly from {@link com.amazonaws.auth.DefaultAWSCredentialsProviderChain}, but
     * has {@link V2ProfileCredentialsProvider} as the first link in the chain.
     */
    protected static class DefaultAwsCredentialsProviderChainPlusSso extends AWSCredentialsProviderChain {
        private static final DefaultAwsCredentialsProviderChainPlusSso INSTANCE
            = new DefaultAwsCredentialsProviderChainPlusSso();

        public DefaultAwsCredentialsProviderChainPlusSso() {
            super(new V2ProfileCredentialsProvider(),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                WebIdentityTokenCredentialsProvider.create(),
                new ProfileCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
        }

        public static DefaultAwsCredentialsProviderChainPlusSso getInstance() {
            return INSTANCE;
        }
    }

    public static void main(String[] args) {
        AmazonS3 client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(DefaultAwsCredentialsProviderChainPlusSso.getInstance())
                .build();
        // The line below will print out your secret access key if the shim gets used. Uncomment if you're okay displaying
        // sensitive info.
        // System.out.println(DefaultAwsCredentialsProviderChainPlusSso.getInstance().getCredentials().toString());
        client.doesBucketExistV2("thisshouldnotexist");
    }
}
