// Live preset preview for the media page: the app's own shader (PresetShader) in WebGL2, with the
// numbers the phone computes (RenderParams). No colour maths lives here, so the preview follows the app.
(function () {
  "use strict";

  var NUMS = ["uExposure", "uContrast", "uHighlights", "uShadows", "uWhites", "uBlacks", "uVibrance", "uSaturation",
    "uBalance", "uBlending", "uShadowTint", "uTexture", "uClarity", "uDehaze", "uSharpen", "uMasking", "uNoise",
    "uVignette", "uVigMid", "uVigFeather", "uVigRound", "uGrain", "uGrainSize"];

  function supported() {
    try { return !!document.createElement("canvas").getContext("webgl2"); } catch (e) { return false; }
  }

  /** One renderer per canvas: compiled once, then each photo and each preset only swap textures and numbers. */
  function Renderer(canvas, shader) {
    var gl = canvas.getContext("webgl2", { preserveDrawingBuffer: true, premultipliedAlpha: false });
    if (!gl) throw new Error("Sem WebGL2");
    this.gl = gl;
    this.canvas = canvas;
    this.prog = link(gl, shader.vertex, shader.fragment);
    this.src = gl.createTexture();
    this.lut = gl.createTexture();
    this.width = 0; this.height = 0;
    var buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    var pos = gl.getAttribLocation(this.prog, "aPos");
    gl.enableVertexAttribArray(pos);
    gl.vertexAttribPointer(pos, 2, gl.FLOAT, false, 0, 0);
  }

  function compile(gl, type, source) {
    var s = gl.createShader(type);
    gl.shaderSource(s, source);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    return s;
  }

  function link(gl, vs, fs) {
    var p = gl.createProgram();
    gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, vs));
    gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, fs));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    return p;
  }

  /** The photo, with mipmaps: clarity, texture and sharpening read its blurred levels, as in the app. */
  Renderer.prototype.setImage = function (img) {
    var gl = this.gl;
    gl.bindTexture(gl.TEXTURE_2D, this.src);
    // The canvas counts rows from the bottom; flipping on upload shows the photo upright.
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    this.width = img.naturalWidth || img.width;
    this.height = img.naturalHeight || img.height;
    this.canvas.width = this.width;
    this.canvas.height = this.height;
  };

  /** Draws the photo with one preset's numbers ([p] as /api/c/params sends them). */
  Renderer.prototype.draw = function (p) {
    var gl = this.gl, prog = this.prog;
    function loc(n) { return gl.getUniformLocation(prog, n); }
    var bytes = new Uint8Array(256 * 4);
    for (var i = 0; i < 256; i++) {
      bytes[i * 4] = p.lut[i * 3]; bytes[i * 4 + 1] = p.lut[i * 3 + 1]; bytes[i * 4 + 2] = p.lut[i * 3 + 2]; bytes[i * 4 + 3] = 255;
    }
    gl.useProgram(prog);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, this.lut);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 256, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, bytes);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.uniform1i(loc("uLut"), 1);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.src);
    gl.uniform1i(loc("uSrc"), 0);

    gl.uniform2f(loc("uSize"), this.width, this.height);
    // Blur radii as in the app: 1/2 and 1/16 of the image.
    gl.uniform1f(loc("uSmallLod"), 1);
    gl.uniform1f(loc("uLargeLod"), 4);
    gl.uniform3fv(loc("uWb"), p.uWb);
    NUMS.forEach(function (n) { gl.uniform1f(loc(n), p[n]); });
    gl.uniform3fv(loc("uHsl"), p.uHsl);
    gl.uniform4fv(loc("uGrading"), p.uGrading);
    gl.uniform2fv(loc("uCalib"), p.uCalib);
    gl.uniform1i(loc("uMono"), p.uMono);
    gl.uniform1fv(loc("uGray"), p.uGray);

    gl.viewport(0, 0, this.width, this.height);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  };

  window.JRLook = { supported: supported, Renderer: Renderer };
})();
