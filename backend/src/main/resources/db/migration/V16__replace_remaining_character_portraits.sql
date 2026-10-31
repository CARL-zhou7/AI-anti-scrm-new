-- Replace the three remaining seed portrait pairs; historical snapshots keep their original keys.
START TRANSACTION;
UPDATE scammer_profile
SET avatar_key='avatars/0a4aa6f76d42c7c40c3646efab6a46af918b808596907e3853c4cd9ea90e0201.png',
    speaking_avatar_key='avatars/fb78d9424abf461f7163d9958fb9f4b9ce1587e6b6e823f8d5792149dc02163f.png',
    row_version=row_version+1
WHERE id=2 AND name='梁悦'
    AND avatar_key='avatars/e548228c03c402e1d2d77d6609678b9712a53767ceae3fe2df9aa134493ceb98.png';
UPDATE victim_profile
SET avatar_key='avatars/54db4e6e556bf56bbc3b01d61d2de990afbbfec64739d51bd6e98e1f179add18.png',
    speaking_avatar_key='avatars/3085547af195e0e19fb69e7f9df96d5c1fe4ab796ef7e903bda568ef9fb9a63e.png',
    row_version=row_version+1
WHERE id=2 AND name='赵诚'
    AND avatar_key='avatars/5e64f9d6f168a553a79eb7db3f00e1941e361e6456d77aa5861f80cf87d97433.png';
UPDATE victim_profile
SET avatar_key='avatars/39e82bce528e303444502835278ae3123fc15d204539dfd1e6ea011dda82e439.png',
    speaking_avatar_key='avatars/08b6ff4d5d6a9e5d5a72c5d777c4c6d1396e87679b0bc7f76701d833f3c24c01.png',
    row_version=row_version+1
WHERE id=3 AND name='周国良'
    AND avatar_key='avatars/2f7dcbc559d76bfaa1646c04f44447c8dcff939f775bf1634293675789b81e93.png';
COMMIT;

