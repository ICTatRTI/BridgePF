package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

public class ConsentServiceImpl implements ConsentService, ApplicationEventPublisherAware {

    private Client stormpathClient;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;
    private ApplicationEventPublisher publisher;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setHealthCodeEncryptor(BridgeEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public User consentToResearch(final User caller, final ConsentSignature consentSignature, final Study study,
            final boolean sendEmail) throws BridgeServiceException {
        if (caller.isConsent()) {
            throw new BridgeServiceException("User has already consented.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        } else if (consentSignature == null) {
            throw new BadRequestException("Consent signature is required.");
        } else if (StringUtils.isBlank(consentSignature.getName())) {
            throw new BadRequestException("Consent full name is required.");
        } else if (consentSignature.getBirthdate() == null) {
            throw new BadRequestException("Consent birth date  is required.");
        }
        try {
            // Stormpath account
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();
            // HealthID
            final String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            final HealthId healthId = getHealthId(healthIdKey, customData); // This sets the ID, which we will need when fully
            // Give consent
            final StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.giveConsent(healthId.getCode(), studyConsent, consentSignature);
            // Publish event
            publisher.publishEvent(new UserEnrolledEvent(caller, study));
            // Sent email
            if (sendEmail) {
                sendMailService.sendConsentAgreement(caller, consentSignature, studyConsent);
            }
            // Update user
            caller.setConsent(true);
            caller.setHealthDataCode(healthId.getCode());
            return caller;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public boolean hasUserConsentedToResearch(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            final String healthCode = caller.getHealthDataCode();
            List<StudyConsent> consents = studyConsentDao.getConsents(study.getKey());
            for (StudyConsent consent : consents) {
                if (userConsentDao.hasConsented(healthCode, consent)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public User withdrawConsent(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        String healthCode = caller.getHealthDataCode();
        List<StudyConsent> consents = studyConsentDao.getConsents(study.getKey());
        for (StudyConsent consent : consents) {
            if (userConsentDao.hasConsented(healthCode, consent)) {
                userConsentDao.withdrawConsent(healthCode, consent);
                publisher.publishEvent(new UserUnenrolledEvent(caller, study));
            }
        }
        caller.setConsent(false);
        return caller;
    }

    @Override
    public void emailConsentAgreement(final User caller, final Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            final StudyConsent consent = studyConsentDao.getConsent(study.getKey());
            if (consent == null) {
                throw new BridgeServiceException("Consent not found.");
            }
            ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthDataCode(), consent);
            if (consentSignature == null) {
                throw new BridgeServiceException("Consent signature not found.");
            }
            sendMailService.sendConsentAgreement(caller, consentSignature, consent);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public User suspendDataSharing(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.suspendSharing(caller.getHealthDataCode(), studyConsent);
            caller.setDataSharing(false);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
        return caller;
    }

    @Override
    public User resumeDataSharing(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.resumeSharing(caller.getHealthDataCode(), studyConsent);
            caller.setDataSharing(true);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
        return caller;
    }

    @Override
    public boolean isSharingData(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            return userConsentDao.isSharingData(caller.getHealthDataCode(), studyConsent);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    private HealthId getHealthId(String healthIdKey, CustomData customData) {
        Object healthIdObj = customData.get(healthIdKey);
        if (healthIdObj != null) {
            final String healthId = healthCodeEncryptor.decrypt((String) healthIdObj);
            final String healthCode = healthCodeService.getHealthCode(healthId);
            return new HealthId() {
                @Override
                public String getId() {
                    return healthId;
                }

                @Override
                public String getCode() {
                    return healthCode;
                }
            };
        }
        HealthId healthId = healthCodeService.create();
        customData.put(healthIdKey, healthCodeEncryptor.encrypt(healthId.getId()));
        customData.put(BridgeConstants.CUSTOM_DATA_VERSION, 1);
        customData.save();
        return healthId;
    }
}
