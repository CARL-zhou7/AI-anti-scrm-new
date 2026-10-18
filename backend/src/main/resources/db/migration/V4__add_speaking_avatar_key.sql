ALTER TABLE scammer_profile ADD COLUMN speaking_avatar_key VARCHAR(255) NULL AFTER avatar_key;
ALTER TABLE victim_profile  ADD COLUMN speaking_avatar_key VARCHAR(255) NULL AFTER avatar_key;
-- Five seed portraits: open-mouth frames produced by tools/generate-avatars.py (same 512x512 canvas).
UPDATE scammer_profile SET speaking_avatar_key = 'avatars/f6251f3b667ff5ab944425676b3ff8da2743d76e902990fa9ad42d7e453a734b.png' WHERE avatar_key = 'avatars/3e1d7c5636e6bafa6dd598a71ebb482c086430ae55b03d88a31049de2421becc.png';
UPDATE scammer_profile SET speaking_avatar_key = 'avatars/8ebe22595c4e85b9448bf0ab60b0938311f51a8464df158668d1483110d07a2a.png' WHERE avatar_key = 'avatars/e548228c03c402e1d2d77d6609678b9712a53767ceae3fe2df9aa134493ceb98.png';
UPDATE victim_profile  SET speaking_avatar_key = 'avatars/14ae6777d18ef5594528c6cc9d7d183dbedc75db9ff1a0cf84cba1554d674de0.png' WHERE avatar_key = 'avatars/255bd304e6384b3ac5149c348273fb3fcc433757206484a933e333426fb8a4c6.png';
UPDATE victim_profile  SET speaking_avatar_key = 'avatars/c5bd3680de6068e2f664085881c8c4ec318c433a120d313a59e3d5e123b69fc8.png' WHERE avatar_key = 'avatars/5e64f9d6f168a553a79eb7db3f00e1941e361e6456d77aa5861f80cf87d97433.png';
UPDATE victim_profile  SET speaking_avatar_key = 'avatars/4b51567b5f98a9b492561a9065dd842a664786de5d1a42131a3b2b31551f83d4.png' WHERE avatar_key = 'avatars/36b4e45ddf67bffe1bdfb7cfb9f8c47a5166e25dc86fb2ddab42cc46b66f358b.png';
-- Profiles added by administrators before V1.1 have no open-mouth frame: temporarily reuse the closed frame
-- (dimensions trivially match); the startup check lists them as WARN so an administrator re-uploads.
UPDATE scammer_profile SET speaking_avatar_key = avatar_key WHERE speaking_avatar_key IS NULL;
UPDATE victim_profile  SET speaking_avatar_key = avatar_key WHERE speaking_avatar_key IS NULL;
ALTER TABLE scammer_profile MODIFY speaking_avatar_key VARCHAR(255) NOT NULL;
ALTER TABLE victim_profile  MODIFY speaking_avatar_key VARCHAR(255) NOT NULL;
