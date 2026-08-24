const multer = require('multer');
const path = require('path');

// uploads buffered in memory then streamed to gridfs
const storage = multer.memoryStorage();

const fileFilter = (req, file, cb) => {
  const allowedMimes = [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp'
  ];

  if (allowedMimes.includes(file.mimetype)) {
    console.log(`[OK] Image file accepted: ${file.originalname} (${file.mimetype})`);
    cb(null, true);
  } else {
    console.error(`[ERROR] Invalid file type: ${file.mimetype}`);
    cb(new Error('Only JPEG, PNG, GIF, and WebP images are allowed'), false);
  }
};

const upload = multer({
  storage: storage,
  fileFilter: fileFilter,
  limits: {
    fileSize: 5 * 1024 * 1024
  }
});

module.exports = {
  upload,
  maxFileSize: 5 * 1024 * 1024,
  allowedExtensions: ['.jpg', '.jpeg', '.png', '.gif', '.webp'],
  allowedMimes: [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp'
  ]
};
