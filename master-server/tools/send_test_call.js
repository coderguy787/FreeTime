// send_test_call.js
// Usage: node send_test_call.js <FCM_TOKEN>
// Sends a data-only incomingCall FCM to the provided token using the existing Firebase Admin SDK setup

const path = require('path');
const fs = require('fs');

const token = process.argv[2];
if (!token) {
  console.error('Usage: node send_test_call.js <FCM_TOKEN>');
  process.exit(1);
}

const admin = require('firebase-admin');
const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT || path.join(__dirname, '..', 'config', 'firebase-service-account.json');

if (!fs.existsSync(serviceAccountPath)) {
  console.error('Firebase service account not found at', serviceAccountPath);
  process.exit(2);
}

const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: serviceAccount.project_id
});

(async () => {
  try {
    const now = new Date();
    const fcmMessage = {
      token: token,
      data: {
        type: 'incomingCall',
        callId: 'test-call-' + now.getTime(),
        callerId: 'test-caller',
        callerName: 'Test Caller',
        callerAvatar: '',
        callType: 'audio',
        offer: '',
        offerSdp: ''
      },
      android: {
        priority: 'high',
        ttl: 60000,
        direct_boot_ok: true
      }
    };

    const resp = await admin.messaging().send(fcmMessage);
    console.log('FCM send response:', resp);
    process.exit(0);
  } catch (err) {
    console.error('FCM send error:', err);
    process.exit(3);
  }
})();
