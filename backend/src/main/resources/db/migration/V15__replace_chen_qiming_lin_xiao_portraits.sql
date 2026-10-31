-- Replace the seed/current uploaded portraits; historical snapshots keep their original keys.
START TRANSACTION;
UPDATE scammer_profile
SET avatar_key='avatars/48d6c708ae546f4d0ca4d17d9f98eda89177ddcc480133070a89f7e42dc642ec.png',
    speaking_avatar_key='avatars/d32624d7261baaeb14726fdcc1332a1b1865b57341d6c0e6b1f940f7b2b849e5.png',
    row_version=row_version+1
WHERE id=1 AND name='陈启明' AND avatar_key IN (
    'avatars/3e1d7c5636e6bafa6dd598a71ebb482c086430ae55b03d88a31049de2421becc.png',
    'avatars/fa3234fc316b3bf238d4d89f33fd8f89f1af49ce06d3d6bebea5613441c84e38.png');
UPDATE victim_profile
SET avatar_key='avatars/f2069504f87a663eabb24812791b5d6df296327342162c46d6fdd84f5557b9f9.png',
    speaking_avatar_key='avatars/be9986d6be1a73dfb392811fb0d75b9b020854e44e55b1a2efb4adf306678c07.png',
    row_version=row_version+1
WHERE id=1 AND name='林晓' AND avatar_key IN (
    'avatars/255bd304e6384b3ac5149c348273fb3fcc433757206484a933e333426fb8a4c6.png',
    'avatars/15771b4cfa7c2c7566987eac5963ee2e8222920206f0fbf8108196e9f6aa838f.png');
COMMIT;
