import * as THREE from 'three';
import { frameDelta, transitionPose, pointerPose, needsSceneFrame } from './animation-clock.mjs';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';

const root = document.querySelector('[data-logo-experience]');
if (root) initialise(root);

async function initialise(root) {
    const find = (selector) => root.querySelector(selector);
    const all = (selector) => [...root.querySelectorAll(selector)];
    const canvas = find('[data-logo-canvas]');
    const stage = find('[data-logo-stage]');
    const inspector = find('[data-logo-inspector]');
    const entryButton = find('[data-logo-open]');
    const status = find('[data-logo-status]');
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
    const state = {
        mode: 'sculpture',
        node: 'trainer',
        expanded: false,
        paused: reducedMotion.matches,
        speed: 0.7,
        glow: 0.55,
        separation: 0,
        reps: 0,
        rotation: 0,
        tilt: 0,
        zoom: 1,
        wireframe: false
    };
    const nodes = [];
    const links = [];
    const materials = new Set();
    const parts = [];
    const palettes = {
        signature: ['#08baff', '#ff761a'],
        aurora: ['#9b6eff', '#56f2c0'],
        solar: ['#fc4b98', '#ffc65c']
    };
    let renderer,
        composer,
        bloom,
        model,
        wings = [],
        frame = 0,
        previous = 0,
        elapsed = 0,
        visible = true;
    let openAmount = 0,
        explodedAmount = 0,
        turn = 0,
        tilt = 0,
        transition = 1,
        transitionFrom = 0,
        cinematicTransition = false,
        renderRequested = false;
    let hoveredNode = null;
    let interactionBounds = null;
    let gesture = null;
    let transitionTurn = 0,
        transitionTilt = 0;
    let returnFocus = null,
        pulse = 0;
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(39, 1, 0.1, 100);
    camera.position.set(0, 0.25, 10);
    const world = new THREE.Group();
    scene.add(world);
    const network = new THREE.Group();
    world.add(network);
    const raycaster = new THREE.Raycaster();
    const pointer = new THREE.Vector2();
    const keyLight = new THREE.DirectionalLight(0xe0f4ff, 1.8);
    keyLight.position.set(-3, 6, 7);
    scene.add(keyLight);
    const warmLight = new THREE.PointLight(0xff8538, 10, 20);
    warmLight.position.set(4, 1, 3);
    scene.add(warmLight);
    const coolLight = new THREE.PointLight(0x32bfff, 9, 20);
    coolLight.position.set(-4, 0, 3);
    scene.add(coolLight);
    scene.add(new THREE.HemisphereLight(0xc0eaff, 0x082735, 0.8));

    function announce(text) {
        status.textContent = text;
    }
    function markMode(mode, cinematic = false) {
        endGesture();
        cinematicTransition = cinematic;
        root.dataset.phase = cinematic ? 'aligning' : 'changing';
        state.mode = mode;
        root.dataset.mode = mode;
        all('[data-logo-mode]').forEach((button) =>
            button.setAttribute('aria-pressed', String(button.dataset.logoMode === mode))
        );
        state.separation = mode === 'exploded' ? 0.8 : 0;
        find('[data-logo-separation]').value = String(state.separation * 100);
        transitionFrom = openAmount;
        transitionTurn = turn;
        transitionTilt = tilt;
        state.rotation = 0;
        state.tilt = 0;
        transition = 0;
        renderStory();
        wake();
    }
    function revealInspector(reveal) {
        if (root.dataset.details === String(reveal)) return;
        root.dataset.details = String(reveal);
        inspector.inert = !reveal;
        inspector.setAttribute('aria-hidden', String(!reveal));
    }
    function openExperience() {
        if (state.expanded) return;
        returnFocus = document.activeElement;
        state.expanded = true;
        root.dataset.expanded = 'true';
        entryButton.setAttribute('aria-expanded', 'true');
        markMode('network', true);
        if (!model || root.classList.contains('logo-fallback')) revealInspector(true);
        (canvas.hidden ? find('[data-logo-close]') : canvas).focus({ preventScroll: true });
    }
    function closeExperience() {
        state.expanded = false;
        root.dataset.expanded = 'false';
        entryButton.setAttribute('aria-expanded', 'false');
        revealInspector(false);
        markMode('sculpture');
        state.zoom = 1;
        find('[data-logo-zoom]').value = '100';
        resize();
        if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true });
    }
    function renderStory() {
        const construction = state.mode === 'exploded';
        root.dataset.node = state.node;
        root.style.setProperty('--rep-angle', `${state.reps * 60}deg`);
        all('[data-logo-rep-count]').forEach((element) => (element.textContent = String(state.reps)));
        all('[data-logo-rep-dot]').forEach((element, index) =>
            element.classList.toggle('is-complete', index < state.reps)
        );
        find('[data-logo-construction]').hidden = !construction;
        find('[data-logo-manipulation]').hidden = !construction;
        canvas.setAttribute('aria-describedby', construction ? 'logo-manipulation-hint' : 'logo-hint');
        find('[data-logo-demo-actions]').hidden = construction;
        all('[data-logo-story]').forEach(
            (el) => (el.hidden = construction || el.dataset.logoStory !== state.node)
        );
        all('[data-logo-node]').forEach((el) =>
            el.setAttribute('aria-pressed', String(el.dataset.logoNode === state.node))
        );
        find('[data-logo-rep]').hidden = state.node !== 'workout';
        find('[data-logo-next]').hidden = state.node === 'workout';
        find('[data-logo-demo-progress]').textContent =
            state.reps === 6 ? root.dataset.repsComplete : root.dataset.repsLabel.replace('{0}', state.reps);
        find('[data-logo-node-labels]').hidden = state.mode !== 'network';
    }
    function chooseNode(key) {
        state.node = key;
        if (state.mode !== 'network') markMode('network');
        renderStory();
        pulse = 1;
        wake();
    }
    function applyPalette(left, right) {
        materials.forEach((mat) => {
            const isLeft = mat.name.includes('Cyan');
            const isRight = mat.name.includes('Orange');
            if (isLeft || isRight) {
                mat.color.set(isLeft ? left : right);
                if (mat.name.startsWith('Light_')) mat.emissive.set(isLeft ? left : right);
            }
        });
        find('[data-logo-colour="left"]').value = left;
        find('[data-logo-colour="right"]').value = right;
        coolLight.color.set(left);
        warmLight.color.set(right);
        wake();
    }
    find('[data-logo-open]').addEventListener('click', openExperience);
    find('[data-logo-close]').addEventListener('click', closeExperience);
    find('[data-logo-implode]').addEventListener('click', () => markMode('sculpture'));
    root.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && state.expanded) {
            event.preventDefault();
            closeExperience();
        }
    });
    all('[data-logo-mode]').forEach((button) =>
        button.addEventListener('click', () => markMode(button.dataset.logoMode))
    );
    all('[data-logo-node]').forEach((button) => {
        button.addEventListener('click', () => chooseNode(button.dataset.logoNode));
        const highlight = () => {
            hoveredNode = button.dataset.logoNode;
            wake();
        };
        const clear = () => {
            hoveredNode = null;
            wake();
        };
        button.addEventListener('pointerenter', highlight);
        button.addEventListener('pointerleave', clear);
        button.addEventListener('focus', highlight);
        button.addEventListener('blur', clear);
    });
    find('[data-logo-next]').addEventListener('click', () => {
        const order = ['trainer', 'gym', 'workout', 'activity'];
        chooseNode(order[(order.indexOf(state.node) + 1) % order.length]);
    });
    find('[data-logo-rep]').addEventListener('click', () => {
        state.reps = Math.min(6, state.reps + 1);
        pulse = 1;
        renderStory();
        if (state.reps === 6) chooseNode('activity');
        wake();
    });
    find('[data-logo-palette]').addEventListener('change', (event) =>
        applyPalette(...palettes[event.target.value])
    );
    all('[data-logo-colour]').forEach((input) =>
        input.addEventListener('input', () =>
            applyPalette(find('[data-logo-colour="left"]').value, find('[data-logo-colour="right"]').value)
        )
    );
    find('[data-logo-separation]').addEventListener('input', (event) => {
        state.separation = Number(event.target.value) / 100;
        wake();
    });
    find('[data-logo-glow]').addEventListener('input', (event) => {
        state.glow = Number(event.target.value) / 100;
        wake();
    });
    find('[data-logo-zoom]').addEventListener('input', (event) => {
        state.zoom = Number(event.target.value) / 100;
        resize();
    });
    find('[data-logo-speed]').addEventListener('input', (event) => {
        state.speed = Number(event.target.value) / 100;
        wake();
    });
    find('[data-logo-wireframe]').addEventListener('change', (event) => {
        state.wireframe = event.target.checked;
        materials.forEach((mat) => (mat.wireframe = state.wireframe));
        wake();
    });
    const pauseButton = find('[data-logo-pause]');
    pauseButton.setAttribute('aria-pressed', String(state.paused));
    pauseButton.addEventListener('click', () => {
        state.paused = !state.paused;
        pauseButton.setAttribute('aria-pressed', String(state.paused));
        wake();
    });
    reducedMotion.addEventListener('change', () => {
        state.paused = reducedMotion.matches;
        pauseButton.setAttribute('aria-pressed', String(state.paused));
        wake();
    });
    find('[data-logo-reset]').addEventListener('click', () => {
        Object.assign(state, {
            rotation: 0,
            tilt: 0,
            zoom: 1,
            separation: 0,
            speed: 0.7,
            glow: 0.55,
            reps: 0,
            node: 'trainer',
            wireframe: false,
            paused: reducedMotion.matches
        });
        find('[data-logo-palette]').value = 'signature';
        find('[data-logo-speed]').value = '70';
        find('[data-logo-glow]').value = '55';
        find('[data-logo-zoom]').value = '100';
        resize();
        find('[data-logo-wireframe]').checked = false;
        materials.forEach((mat) => (mat.wireframe = false));
        pauseButton.setAttribute('aria-pressed', String(state.paused));
        applyPalette(...palettes.signature);
        markMode('sculpture');
    });
    function resetPointer() {
        endGesture();
        interactionBounds = null;
        if (state.mode === 'exploded') return;
        state.rotation = 0;
        state.tilt = 0;
        wake();
    }
    stage.addEventListener('pointerenter', () => {
        interactionBounds = stage.getBoundingClientRect();
    });
    stage.addEventListener('pointermove', (event) => {
        if (state.mode === 'exploded') {
            if (!gesture || gesture.id !== event.pointerId) return;
            const dx = event.clientX - gesture.x;
            const dy = event.clientY - gesture.y;
            state.rotation = gesture.rotation + dx * 0.009;
            state.tilt = THREE.MathUtils.clamp(gesture.tilt + dy * 0.009, -1.3, 1.3);
            wake();
            return;
        }
        if (event.target.closest('button')) return;
        if (event.pointerType === 'touch' || reducedMotion.matches || state.paused || transition < 1) return;
        interactionBounds ??= stage.getBoundingClientRect();
        const pose = pointerPose(event.clientX, event.clientY, interactionBounds);
        state.rotation = pose.rotation;
        state.tilt = pose.tilt;
        wake();
    });
    stage.addEventListener('pointerleave', () => {
        if (state.mode !== 'exploded') resetPointer();
    });
    stage.addEventListener('pointercancel', resetPointer);
    window.addEventListener('blur', resetPointer);
    window.addEventListener('scroll', resetPointer, { passive: true });
    window.addEventListener('resize', resetPointer);
    function endGesture() {
        if (!gesture) return;
        const id = gesture.id;
        gesture = null;
        root.dataset.dragging = 'false';
        if (canvas.hasPointerCapture(id)) canvas.releasePointerCapture(id);
    }
    canvas.addEventListener('pointerdown', (event) => {
        if (state.mode !== 'exploded' || !model || transition < 1 || !event.isPrimary || ![0, 2].includes(event.button)) return;
        event.preventDefault();
        canvas.focus({ preventScroll: true });
        gesture = { id: event.pointerId, x: event.clientX, y: event.clientY,
            rotation: state.rotation, tilt: state.tilt };
        canvas.setPointerCapture(event.pointerId);
        root.dataset.dragging = 'true';
    });
    canvas.addEventListener('pointerup', endGesture);
    canvas.addEventListener('lostpointercapture', endGesture);
    canvas.addEventListener('contextmenu', (event) => {
        if (state.mode === 'exploded') event.preventDefault();
    });
    canvas.addEventListener('click', (event) => {
        if (state.mode === 'exploded') return;
        if (!state.expanded) {
            openExperience();
            return;
        }
        if (transition < 1) return;
        const rect = canvas.getBoundingClientRect();
        pointer.set(
            ((event.clientX - rect.left) / rect.width) * 2 - 1,
            -((event.clientY - rect.top) / rect.height) * 2 + 1
        );
        raycaster.setFromCamera(pointer, camera);
        const hit = raycaster.intersectObjects(
            nodes.map((node) => node.mesh),
            false
        )[0];
        if (hit && state.mode === 'network') chooseNode(hit.object.userData.key);
        else if (state.mode === 'sculpture') markMode('network');
    });
    canvas.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            state.expanded ? markMode(state.mode === 'network' ? 'sculpture' : 'network') : openExperience();
        }
        if (event.key.startsWith('Arrow')) {
            event.preventDefault();
            if (event.key === 'ArrowLeft') state.rotation -= 0.2;
            if (event.key === 'ArrowRight') state.rotation += 0.2;
            const tiltLimit = state.mode === 'exploded' ? 1.3 : 0.55;
            if (event.key === 'ArrowUp') state.tilt = Math.max(-tiltLimit, state.tilt - 0.1);
            if (event.key === 'ArrowDown') state.tilt = Math.min(tiltLimit, state.tilt + 0.1);
            wake();
        }
    });

    try {
        renderer = new THREE.WebGLRenderer({
            canvas,
            antialias: true,
            alpha: true,
            powerPreference: 'high-performance'
        });
        renderer.setPixelRatio(Math.min(devicePixelRatio, 1.65));
        renderer.toneMapping = THREE.ACESFilmicToneMapping;
        renderer.toneMappingExposure = 0.86;
        renderer.setClearColor(0x000000, 0);
        const pmrem = new THREE.PMREMGenerator(renderer);
        const room = new RoomEnvironment();
        scene.environment = pmrem.fromScene(room, 0.02).texture;
        scene.environmentIntensity = 0.4;
        room.dispose();
        pmrem.dispose();
        composer = new EffectComposer(renderer);
        composer.addPass(new RenderPass(scene, camera));
        bloom = new UnrealBloomPass(new THREE.Vector2(1, 1), 0.1, 0.28, 1.35);
        composer.addPass(bloom);
        composer.addPass(new OutputPass());
        const gltf = await new GLTFLoader().loadAsync(root.dataset.modelUrl);
        model = gltf.scene;
        model.traverse((object) => {
            if (!object.isMesh) return;
            object.material = object.material.clone();
            object.material.emissiveIntensity = Math.min(object.material.emissiveIntensity, 0.4);
            // Satin enamel keeps its colour when the pointer tilts it into the studio lights.
            if (object.name.startsWith('Face_')) {
                object.material.metalness = 0.28;
                object.material.roughness = 0.42;
                object.material.envMapIntensity = 0.35;
                if ('clearcoat' in object.material) {
                    object.material.clearcoat = 0.2;
                    object.material.clearcoatRoughness = 0.45;
                }
            } else if (!object.material.name.startsWith('Light_')) {
                object.material.roughness = Math.max(object.material.roughness, 0.3);
                object.material.envMapIntensity = 1.1;
            }
            materials.add(object.material);
            parts.push({
                object,
                base: object.position.clone(),
                layer: object.name.startsWith('Face_')
                    ? 1
                    : object.name.startsWith('Circuit_')
                      ? 0.25
                      : object.name.startsWith('Shell_')
                        ? -0.75
                        : object.name.startsWith('Core_')
                          ? 1.7
                          : object.name.startsWith('Contact_')
                            ? 1
                            : 0
            });
        });
        wings = [model.getObjectByName('Wing_Left'), model.getObjectByName('Wing_Right')];
        if (wings.some((wing) => !wing)) throw new Error('Sculpture is missing its hinged wings');
        world.add(model);
        buildNetwork();
        buildAtmosphere();
        applyPalette(...palettes.signature);
        resize();
        root.dataset.ready = 'true';
        announce(root.dataset.statusReady);
        wake();
    } catch (error) {
        showFallback();
        // All narrative controls remain usable even if WebGL or the model is unavailable.
        console.warn('One To One sculpture unavailable:', error.message);
        renderer?.dispose();
        renderer = null;
    }

    function showFallback() {
        root.dataset.ready = 'fallback';
        canvas.hidden = true;
        root.classList.add('logo-fallback');
        announce(root.dataset.statusFallback);
        if (state.expanded) revealInspector(true);
    }

    function buildNetwork() {
        const positions = [
            [-2.05, 1.15, 1.1],
            [2.05, 1.15, 1.1],
            [-1.65, -1.0, 1.65],
            [1.65, -1.0, 1.65]
        ];
        const colours = [0x25d6ff, 0xffa348, 0xaa8bff, 0x5fffc3];
        ['trainer', 'gym', 'workout', 'activity'].forEach((key, index) => {
            const group = new THREE.Group();
            group.position.set(...positions[index]);
            network.add(group);
            const mat = new THREE.MeshPhysicalMaterial({
                color: colours[index],
                metalness: 0.65,
                roughness: 0.18,
                clearcoat: 1,
                emissive: colours[index],
                emissiveIntensity: 0.3
            });
            const mesh = new THREE.Mesh(new THREE.IcosahedronGeometry(0.24, 3), mat);
            mesh.userData.key = key;
            group.add(mesh);
            const halo = new THREE.Mesh(
                new THREE.TorusGeometry(0.37, 0.013, 6, 64),
                new THREE.MeshBasicMaterial({ color: colours[index] })
            );
            group.add(halo);
            const outer = halo.clone();
            outer.scale.setScalar(1.3);
            outer.rotation.x = 0.8;
            group.add(outer);
            nodes.push({ key, group, mesh, halo, outer, label: find(`[data-logo-node="${key}"]`) });
        });
        const centre = new THREE.Mesh(
            new THREE.IcosahedronGeometry(0.21, 2),
            new THREE.MeshPhysicalMaterial({
                color: 0xe1fff7,
                emissive: 0x69ffde,
                emissiveIntensity: 0.55,
                roughness: 0.15,
                metalness: 0.45
            })
        );
        centre.position.set(0, 0.1, 1.65);
        network.add(centre);
        nodes.forEach((node, index) => {
            const from = node.group.position;
            const to = centre.position;
            const side = Math.sign(from.x);
            const upper = from.y > 0;
            const curve = new THREE.CubicBezierCurve3(
                from.clone(),
                new THREE.Vector3(from.x + side * 0.65, from.y + (upper ? 0.95 : -0.7), 2.15),
                new THREE.Vector3(-side * 0.85, upper ? 0.85 : -0.9, 2.65),
                to.clone()
            );
            const tube = new THREE.Mesh(
                new THREE.TubeGeometry(curve, 72, 0.014, 6, false),
                new THREE.MeshBasicMaterial({ color: colours[index], transparent: true, opacity: 0.65 })
            );
            network.add(tube);
            const beads = [];
            for (let i = 0; i < 4; i++) {
                const bead = new THREE.Mesh(
                    new THREE.SphereGeometry(0.035, 8, 6),
                    new THREE.MeshBasicMaterial({ color: colours[index] })
                );
                network.add(bead);
                beads.push(bead);
            }
            links.push({ curve, tube, beads, key: node.key });
        });
        // Each coloured strand follows an organic arc; there is no rectangular perimeter or page insert.
    }
    function buildAtmosphere() {
        const orbitPoints = Array.from({ length: 24 }, (_, index) => {
            const angle = (index / 24) * Math.PI * 2;
            const radius = 2.9 + Math.sin(angle * 3) * 0.2;
            return new THREE.Vector3(
                Math.cos(angle) * radius,
                -2.35 + Math.sin(angle * 2) * 0.14,
                Math.sin(angle) * radius
            );
        });
        const orbit = new THREE.CatmullRomCurve3(orbitPoints, true);
        scene.add(
            new THREE.Mesh(
                new THREE.TubeGeometry(orbit, 160, 0.008, 5, true),
                new THREE.MeshBasicMaterial({ color: 0x2287a8, transparent: true, opacity: 0.18 })
            )
        );
        const positions = new Float32Array(150 * 3);
        for (let i = 0; i < 150; i++) {
            const angle = i * 2.39996;
            const radius = 2.7 + (i % 17) / 10;
            positions[i * 3] = Math.cos(angle) * radius;
            positions[i * 3 + 1] = ((i * 13) % 71) / 14 - 2.5;
            positions[i * 3 + 2] = Math.sin(angle) * radius - 1.8;
        }
        const geometry = new THREE.BufferGeometry();
        geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
        scene.add(
            new THREE.Points(
                geometry,
                new THREE.PointsMaterial({
                    color: 0x5feaff,
                    size: 0.024,
                    transparent: true,
                    opacity: 0.65,
                    sizeAttenuation: true
                })
            )
        );
    }
    function resize() {
        if (!renderer) return;
        const { width, height } = stage.getBoundingClientRect();
        if (width <= 0 || height <= 0) return;
        camera.aspect = width / height;
        camera.position.z = (camera.aspect < 0.95 ? 11.7 : 9.4) / state.zoom;
        camera.lookAt(0, 0, 0);
        camera.updateProjectionMatrix();
        renderer.setSize(width, height, false);
        composer.setSize(width, height);
        wake();
    }
    function wake() {
        renderRequested = true;
        if (!renderer || !model || frame || document.hidden) return;
        previous = performance.now();
        frame = requestAnimationFrame(animate);
    }
    function animate(time) {
        frame = 0;
        renderRequested = false;
        const dt = frameDelta(time, previous);
        previous = time;
        const instant = reducedMotion.matches || state.paused;
        if (!instant) elapsed += dt * state.speed;
        const targetOpen = state.mode === 'network' ? 1 : 0;
        transition = instant ? 1 : Math.min(1, transition + dt / (cinematicTransition ? 1.15 : 0.45));
        const pose = transitionPose(transition, cinematicTransition);
        openAmount = THREE.MathUtils.lerp(transitionFrom, targetOpen, pose.open);
        const smooth = instant ? 1 : 1 - Math.exp(-dt * 4.8);
        explodedAmount = THREE.MathUtils.lerp(explodedAmount, state.separation, smooth);
        if (transition < 1) {
            turn = transitionTurn * (1 - pose.align);
            tilt = transitionTilt * (1 - pose.align);
        } else {
            turn = THREE.MathUtils.lerp(turn, state.rotation, smooth);
            tilt = THREE.MathUtils.lerp(tilt, state.tilt, smooth);
        }
        const sideTurn = instant ? 0 : pose.turn * Math.abs(targetOpen - transitionFrom);
        world.rotation.set(0.04 + tilt, -0.16 + turn + sideTurn + openAmount * 0.1, 0);
        root.dataset.phase = transition === 1 ? 'settled' : pose.phase;
        root.dataset.pointerYaw = turn.toFixed(3);
        root.dataset.pointerTilt = tilt.toFixed(3);
        root.dataset.openAmount = openAmount.toFixed(3);
        if (state.expanded && pose.open > 0.9) revealInspector(true);
        world.position.set(0, 0.22 + (instant || state.mode === 'exploded' ? 0 : Math.sin(elapsed * 0.65) * 0.06), 0);
        root.dataset.pivotX = world.position.x.toFixed(3);
        root.dataset.pivotY = world.position.y.toFixed(3);
        model.position.x = -openAmount * 0.08;
        wings.forEach((wing, i) => {
            const sign = i === 0 ? -1 : 1;
            wing.rotation.y = sign * openAmount * 2.05;
            wing.position.x = sign * (explodedAmount * 0.8 + openAmount * 2.4);
        });
        parts.forEach(({ object, base, layer }) =>
            object.position.set(base.x, base.y, base.z + layer * explodedAmount * 1.2)
        );
        network.visible = openAmount > 0.1;
        network.scale.setScalar(Math.max(0.001, openAmount));
        const wordmark = parts.find((part) => part.object.name === 'Wordmark');
        if (wordmark) wordmark.object.position.y = wordmark.base.y - openAmount * 0.32;
        pulse = Math.max(0, pulse - dt * 1.3);
        nodes.forEach((node) => {
            const selected = node.key === state.node;
            const targetScale =
                (selected ? 1.2 : hoveredNode === node.key ? 1.12 : 1) + (selected ? pulse * 0.16 : 0);
            node.mesh.scale.setScalar(THREE.MathUtils.lerp(node.mesh.scale.x, targetScale, smooth));
            node.halo.rotation.y = elapsed * 0.5;
            node.outer.rotation.z = elapsed * 0.3;
            const position = node.group.getWorldPosition(new THREE.Vector3()).project(camera);
            const x = (position.x * 0.5 + 0.5) * stage.clientWidth,
                y = (-position.y * 0.5 + 0.5) * stage.clientHeight;
            node.label.style.transform = `translate(${x.toFixed(1)}px,${(y + 32).toFixed(1)}px) translateX(-50%)`;
            node.label.style.visibility = openAmount > 0.9 ? 'visible' : 'hidden';
        });
        links.forEach(({ curve, tube, beads, key }, index) => {
            tube.material.opacity = THREE.MathUtils.lerp(
                tube.material.opacity,
                key === state.node ? 0.78 : key === hoveredNode ? 0.6 : 0.25,
                smooth
            );
            beads.forEach((bead, i) =>
                bead.position.copy(curve.getPoint((elapsed * 0.22 + i / 4 + index * 0.11) % 1))
            );
        });
        bloom.strength = 0.035 + state.glow * 0.1 + pulse * 0.025;
        renderer.toneMappingExposure = 0.78 + state.glow * 0.16;
        composer.render();
        root.dataset.sceneState =
            transition === 1 && Math.abs(explodedAmount - state.separation) < 0.01
                ? 'settled'
                : 'transitioning';
        const settling =
            Math.abs(explodedAmount - state.separation) > 0.001 ||
            Math.abs(turn - state.rotation) > 0.001 ||
            Math.abs(tilt - state.tilt) > 0.001 ||
            pulse > 0;
        const ambient = !state.paused && state.speed > 0 && !reducedMotion.matches;
        if (
            needsSceneFrame({
                visible,
                hidden: document.hidden,
                transition,
                settling,
                ambient,
                requested: renderRequested
            })
        ) {
            frame = requestAnimationFrame(animate);
        }
    }

    new ResizeObserver(resize).observe(stage);
    new IntersectionObserver(
        (entries) => {
            visible = entries[0].isIntersecting;
            if (visible) wake();
            // A deliberate mode change must finish even if scrolling hides the artwork.
            // The loop itself stops ambient work once finite interaction motion settles.
        },
        { threshold: 0.01 }
    ).observe(stage);
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            cancelAnimationFrame(frame);
            frame = 0;
        } else wake();
    });
    canvas.addEventListener('webglcontextlost', (event) => {
        event.preventDefault();
        cancelAnimationFrame(frame);
        frame = 0;
        showFallback();
    });
    canvas.addEventListener('webglcontextrestored', () => {
        root.dataset.ready = 'true';
        canvas.hidden = false;
        root.classList.remove('logo-fallback');
        announce(root.dataset.statusReady);
        resize();
        wake();
    });
}
