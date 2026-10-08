# AvatarSample_A — source and usage terms

Author / copyright holder: VRoid / pixiv. This asset is **not CC0** and is not covered by this repository's code license.

- Official model: https://hub.vroid.com/en/characters/2843975675147313744/models/5644550979324015604
- Official terms: https://vroid.pixiv.help/hc/en-us/articles/4402394424089-VRoidPreset-A-Z
- Downloaded public redistribution: https://github.com/madjin/vrm-samples/blob/master/vroid/stable/AvatarSample_A.vrm
- Retrieved: 2026-10-08
- Original SHA-256: `b86b0b8a66d48911431d6f920a5211a974226f83aa672eca3f3dfade58ac346e`
- Converted GLB SHA-256: `217767727a441f0b9c0d07102abfc26310069007610b6b5a3d1929a65997c3ba`

The official model page permits commercial use, alteration and redistribution. The linked official conditions remain controlling, including restrictions on selling sample data, using sample data in character creation services, claiming CC0, and implying pixiv endorsement. This note is not a substitute for those conditions. Verify terms for any planned distribution.

The downloaded file identifies itself as AvatarSample_A by VRoid. It is a mirror copy; byte-for-byte equivalence with the current Hub download has not been verified.

## Local derivative

`tools/convert_vroid_avatar.py` retains the complete body and base-color textures, bakes an arms-lowered pose, changes facing/scale, and exports the VRM A and Blink presets as `mouthOpen` and `blink`. Skeleton, spring bones and other facial expressions are omitted from the runtime derivative. The original VRM remains here for future edits.

Rendering uses standard glTF unlit materials, not VRM MToon shading or outlines. Facial animation uses volume-driven opening, not phoneme recognition.
