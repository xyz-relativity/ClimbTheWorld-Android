/* Copyright (c) 2007-2008 CSIRO
   Copyright (c) 2007-2011 Xiph.Org Foundation
   Originally written by Jean-Marc Valin, Gregory Maxwell, Koen Vos,
   Timothy B. Terriberry, and the Opus open-source contributors
   Ported to Java by Logan Stromberg

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   - Neither the name of Internet Society, IETF or IETF Trust, nor the
   names of specific contributors, may be used to endorse or promote
   products derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.concentus;

class OpusTables {

    static final float[] dct_table = {
        0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f,
        0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f, 0.250000f,
        0.351851f, 0.338330f, 0.311806f, 0.273300f, 0.224292f, 0.166664f, 0.102631f, 0.034654f,
        -0.034654f, -0.102631f, -0.166664f, -0.224292f, -0.273300f, -0.311806f, -0.338330f, -0.351851f,
        0.346760f, 0.293969f, 0.196424f, 0.068975f, -0.068975f, -0.196424f, -0.293969f, -0.346760f,
        -0.346760f, -0.293969f, -0.196424f, -0.068975f, 0.068975f, 0.196424f, 0.293969f, 0.346760f,
        0.338330f, 0.224292f, 0.034654f, -0.166664f, -0.311806f, -0.351851f, -0.273300f, -0.102631f,
        0.102631f, 0.273300f, 0.351851f, 0.311806f, 0.166664f, -0.034654f, -0.224292f, -0.338330f,
        0.326641f, 0.135299f, -0.135299f, -0.326641f, -0.326641f, -0.135299f, 0.135299f, 0.326641f,
        0.326641f, 0.135299f, -0.135299f, -0.326641f, -0.326641f, -0.135299f, 0.135299f, 0.326641f,
        0.311806f, 0.034654f, -0.273300f, -0.338330f, -0.102631f, 0.224292f, 0.351851f, 0.166664f,
        -0.166664f, -0.351851f, -0.224292f, 0.102631f, 0.338330f, 0.273300f, -0.034654f, -0.311806f,
        0.293969f, -0.068975f, -0.346760f, -0.196424f, 0.196424f, 0.346760f, 0.068975f, -0.293969f,
        -0.293969f, 0.068975f, 0.346760f, 0.196424f, -0.196424f, -0.346760f, -0.068975f, 0.293969f,
        0.273300f, -0.166664f, -0.338330f, 0.034654f, 0.351851f, 0.102631f, -0.311806f, -0.224292f,
        0.224292f, 0.311806f, -0.102631f, -0.351851f, -0.034654f, 0.338330f, 0.166664f, -0.273300f,};

    static final float[] analysis_window = {
        0.000043f, 0.000171f, 0.000385f, 0.000685f, 0.001071f, 0.001541f, 0.002098f, 0.002739f,
        0.003466f, 0.004278f, 0.005174f, 0.006156f, 0.007222f, 0.008373f, 0.009607f, 0.010926f,
        0.012329f, 0.013815f, 0.015385f, 0.017037f, 0.018772f, 0.020590f, 0.022490f, 0.024472f,
        0.026535f, 0.028679f, 0.030904f, 0.033210f, 0.035595f, 0.038060f, 0.040604f, 0.043227f,
        0.045928f, 0.048707f, 0.051564f, 0.054497f, 0.057506f, 0.060591f, 0.063752f, 0.066987f,
        0.070297f, 0.073680f, 0.077136f, 0.080665f, 0.084265f, 0.087937f, 0.091679f, 0.095492f,
        0.099373f, 0.103323f, 0.107342f, 0.111427f, 0.115579f, 0.119797f, 0.124080f, 0.128428f,
        0.132839f, 0.137313f, 0.141849f, 0.146447f, 0.151105f, 0.155823f, 0.160600f, 0.165435f,
        0.170327f, 0.175276f, 0.180280f, 0.185340f, 0.190453f, 0.195619f, 0.200838f, 0.206107f,
        0.211427f, 0.216797f, 0.222215f, 0.227680f, 0.233193f, 0.238751f, 0.244353f, 0.250000f,
        0.255689f, 0.261421f, 0.267193f, 0.273005f, 0.278856f, 0.284744f, 0.290670f, 0.296632f,
        0.302628f, 0.308658f, 0.314721f, 0.320816f, 0.326941f, 0.333097f, 0.339280f, 0.345492f,
        0.351729f, 0.357992f, 0.364280f, 0.370590f, 0.376923f, 0.383277f, 0.389651f, 0.396044f,
        0.402455f, 0.408882f, 0.415325f, 0.421783f, 0.428254f, 0.434737f, 0.441231f, 0.447736f,
        0.454249f, 0.460770f, 0.467298f, 0.473832f, 0.480370f, 0.486912f, 0.493455f, 0.500000f,
        0.506545f, 0.513088f, 0.519630f, 0.526168f, 0.532702f, 0.539230f, 0.545751f, 0.552264f,
        0.558769f, 0.565263f, 0.571746f, 0.578217f, 0.584675f, 0.591118f, 0.597545f, 0.603956f,
        0.610349f, 0.616723f, 0.623077f, 0.629410f, 0.635720f, 0.642008f, 0.648271f, 0.654508f,
        0.660720f, 0.666903f, 0.673059f, 0.679184f, 0.685279f, 0.691342f, 0.697372f, 0.703368f,
        0.709330f, 0.715256f, 0.721144f, 0.726995f, 0.732807f, 0.738579f, 0.744311f, 0.750000f,
        0.755647f, 0.761249f, 0.766807f, 0.772320f, 0.777785f, 0.783203f, 0.788573f, 0.793893f,
        0.799162f, 0.804381f, 0.809547f, 0.814660f, 0.819720f, 0.824724f, 0.829673f, 0.834565f,
        0.839400f, 0.844177f, 0.848895f, 0.853553f, 0.858151f, 0.862687f, 0.867161f, 0.871572f,
        0.875920f, 0.880203f, 0.884421f, 0.888573f, 0.892658f, 0.896677f, 0.900627f, 0.904508f,
        0.908321f, 0.912063f, 0.915735f, 0.919335f, 0.922864f, 0.926320f, 0.929703f, 0.933013f,
        0.936248f, 0.939409f, 0.942494f, 0.945503f, 0.948436f, 0.951293f, 0.954072f, 0.956773f,
        0.959396f, 0.961940f, 0.964405f, 0.966790f, 0.969096f, 0.971321f, 0.973465f, 0.975528f,
        0.977510f, 0.979410f, 0.981228f, 0.982963f, 0.984615f, 0.986185f, 0.987671f, 0.989074f,
        0.990393f, 0.991627f, 0.992778f, 0.993844f, 0.994826f, 0.995722f, 0.996534f, 0.997261f,
        0.997902f, 0.998459f, 0.998929f, 0.999315f, 0.999615f, 0.999829f, 0.999957f, 1.000000f,};

    static final int[] tbands/*[NB_TBANDS + 1]*/ = {
                2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 68, 80, 96, 120
            };

    static final int[] extra_bands/*[NB_TOT_BANDS + 1]*/ = {
                1, 2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 68, 80, 96, 120, 160, 200
            };

    /*static final float tweight[NB_TBANDS+1] = {
          .3, .4, .5, .6, .7, .8, .9, 1., 1., 1., 1., 1., 1., 1., .8, .7, .6, .5
    };*/

 /* RMS error was 0.138320, seed was 1361535663 */
    static final float[] weights/*[422]*/ = {
                /* hidden layer */
                -0.0941125f, -0.302976f, -0.603555f, -0.19393f, -0.185983f,
                -0.601617f, -0.0465317f, -0.114563f, -0.103599f, -0.618938f,
                -0.317859f, -0.169949f, -0.0702885f, 0.148065f, 0.409524f,
                0.548432f, 0.367649f, -0.494393f, 0.764306f, -1.83957f,
                0.170849f, 12.786f, -1.08848f, -1.27284f, -16.2606f,
                24.1773f, -5.57454f, -0.17276f, -0.163388f, -0.224421f,
                -0.0948944f, -0.0728695f, -0.26557f, -0.100283f, -0.0515459f,
                -0.146142f, -0.120674f, -0.180655f, 0.12857f, 0.442138f,
                -0.493735f, 0.167767f, 0.206699f, -0.197567f, 0.417999f,
                1.50364f, -0.773341f, -10.0401f, 0.401872f, 2.97966f,
                15.2165f, -1.88905f, -1.19254f, 0.0285397f, -0.00405139f,
                0.0707565f, 0.00825699f, -0.0927269f, -0.010393f, -0.00428882f,
                -0.00489743f, -0.0709731f, -0.00255992f, 0.0395619f, 0.226424f,
                0.0325231f, 0.162175f, -0.100118f, 0.485789f, 0.12697f,
                0.285937f, 0.0155637f, 0.10546f, 3.05558f, 1.15059f,
                -1.00904f, -1.83088f, 3.31766f, -3.42516f, -0.119135f,
                -0.0405654f, 0.00690068f, 0.0179877f, -0.0382487f, 0.00597941f,
                -0.0183611f, 0.00190395f, -0.144322f, -0.0435671f, 0.000990594f,
                0.221087f, 0.142405f, 0.484066f, 0.404395f, 0.511955f,
                -0.237255f, 0.241742f, 0.35045f, -0.699428f, 10.3993f,
                2.6507f, -2.43459f, -4.18838f, 1.05928f, 1.71067f,
                0.00667811f, -0.0721335f, -0.0397346f, 0.0362704f, -0.11496f,
                -0.0235776f, 0.0082161f, -0.0141741f, -0.0329699f, -0.0354253f,
                0.00277404f, -0.290654f, -1.14767f, -0.319157f, -0.686544f,
                0.36897f, 0.478899f, 0.182579f, -0.411069f, 0.881104f,
                -4.60683f, 1.4697f, 0.335845f, -1.81905f, -30.1699f,
                5.55225f, 0.0019508f, -0.123576f, -0.0727332f, -0.0641597f,
                -0.0534458f, -0.108166f, -0.0937368f, -0.0697883f, -0.0275475f,
                -0.192309f, -0.110074f, 0.285375f, -0.405597f, 0.0926724f,
                -0.287881f, -0.851193f, -0.099493f, -0.233764f, -1.2852f,
                1.13611f, 3.12168f, -0.0699f, -1.86216f, 2.65292f,
                -7.31036f, 2.44776f, -0.00111802f, -0.0632786f, -0.0376296f,
                -0.149851f, 0.142963f, 0.184368f, 0.123433f, 0.0756158f,
                0.117312f, 0.0933395f, 0.0692163f, 0.0842592f, 0.0704683f,
                0.0589963f, 0.0942205f, -0.448862f, 0.0262677f, 0.270352f,
                -0.262317f, 0.172586f, 2.00227f, -0.159216f, 0.038422f,
                10.2073f, 4.15536f, -2.3407f, -0.0550265f, 0.00964792f,
                -0.141336f, 0.0274501f, 0.0343921f, -0.0487428f, 0.0950172f,
                -0.00775017f, -0.0372492f, -0.00548121f, -0.0663695f, 0.0960506f,
                -0.200008f, -0.0412827f, 0.58728f, 0.0515787f, 0.337254f,
                0.855024f, 0.668371f, -0.114904f, -3.62962f, -0.467477f,
                -0.215472f, 2.61537f, 0.406117f, -1.36373f, 0.0425394f,
                0.12208f, 0.0934502f, 0.123055f, 0.0340935f, -0.142466f,
                0.035037f, -0.0490666f, 0.0733208f, 0.0576672f, 0.123984f,
                -0.0517194f, -0.253018f, 0.590565f, 0.145849f, 0.315185f,
                0.221534f, -0.149081f, 0.216161f, -0.349575f, 24.5664f,
                -0.994196f, 0.614289f, -18.7905f, -2.83277f, -0.716801f,
                -0.347201f, 0.479515f, -0.246027f, 0.0758683f, 0.137293f,
                -0.17781f, 0.118751f, -0.00108329f, -0.237334f, 0.355732f,
                -0.12991f, -0.0547627f, -0.318576f, -0.325524f, 0.180494f,
                -0.0625604f, 0.141219f, 0.344064f, 0.37658f, -0.591772f,
                5.8427f, -0.38075f, 0.221894f, -1.41934f, -1.87943e+06f,
                1.34114f, 0.0283355f, -0.0447856f, -0.0211466f, -0.0256927f,
                0.0139618f, 0.0207934f, -0.0107666f, 0.0110969f, 0.0586069f,
                -0.0253545f, -0.0328433f, 0.11872f, -0.216943f, 0.145748f,
                0.119808f, -0.0915211f, -0.120647f, -0.0787719f, -0.143644f,
                -0.595116f, -1.152f, -1.25335f, -1.17092f, 4.34023f,
                -975268.0f, -1.37033f, -0.0401123f, 0.210602f, -0.136656f,
                0.135962f, -0.0523293f, 0.0444604f, 0.0143928f, 0.00412666f,
                -0.0193003f, 0.218452f, -0.110204f, -2.02563f, 0.918238f,
                -2.45362f, 1.19542f, -0.061362f, -1.92243f, 0.308111f,
                0.49764f, 0.912356f, 0.209272f, -2.34525f, 2.19326f,
                -6.47121f, 1.69771f, -0.725123f, 0.0118929f, 0.0377944f,
                0.0554003f, 0.0226452f, -0.0704421f, -0.0300309f, 0.0122978f,
                -0.0041782f, -0.0686612f, 0.0313115f, 0.039111f, 0.364111f,
                -0.0945548f, 0.0229876f, -0.17414f, 0.329795f, 0.114714f,
                0.30022f, 0.106997f, 0.132355f, 5.79932f, 0.908058f,
                -0.905324f, -3.3561f, 0.190647f, 0.184211f, -0.673648f,
                0.231807f, -0.0586222f, 0.230752f, -0.438277f, 0.245857f,
                -0.17215f, 0.0876383f, -0.720512f, 0.162515f, 0.0170571f,
                0.101781f, 0.388477f, 1.32931f, 1.08548f, -0.936301f,
                -2.36958f, -6.71988f, -3.44376f, 2.13818f, 14.2318f,
                4.91459f, -3.09052f, -9.69191f, -0.768234f, 1.79604f,
                0.0549653f, 0.163399f, 0.0797025f, 0.0343933f, -0.0555876f,
                -0.00505673f, 0.0187258f, 0.0326628f, 0.0231486f, 0.15573f,
                0.0476223f, -0.254824f, 1.60155f, -0.801221f, 2.55496f,
                0.737629f, -1.36249f, -0.695463f, -2.44301f, -1.73188f,
                3.95279f, 1.89068f, 0.486087f, -11.3343f, 3.9416e+06f,
                /* output layer */
                -0.381439f, 0.12115f, -0.906927f, 2.93878f, 1.6388f,
                0.882811f, 0.874344f, 1.21726f, -0.874545f, 0.321706f,
                0.785055f, 0.946558f, -0.575066f, -3.46553f, 0.884905f,
                0.0924047f, -9.90712f, 0.391338f, 0.160103f, -2.04954f,
                4.1455f, 0.0684029f, -0.144761f, -0.285282f, 0.379244f,
                -1.1584f, -0.0277241f, -9.85f, -4.82386f, 3.71333f,
                3.87308f, 3.52558f};

    static final int[] topo = {25, 15, 2};

    // fixme: move this into an MLP class singleton or something?
    static final MLPState net = new MLPState();

    static {
        net.layers = 3;
        net.topo = topo;
        net.weights = weights;
    }

	static final float[] tansig_table/*[201]*/ = {
                0.000000f, 0.039979f, 0.079830f, 0.119427f, 0.158649f,
                0.197375f, 0.235496f, 0.272905f, 0.309507f, 0.345214f,
                0.379949f, 0.413644f, 0.446244f, 0.477700f, 0.507977f,
                0.537050f, 0.564900f, 0.591519f, 0.616909f, 0.641077f,
                0.664037f, 0.685809f, 0.706419f, 0.725897f, 0.744277f,
                0.761594f, 0.777888f, 0.793199f, 0.807569f, 0.821040f,
                0.833655f, 0.845456f, 0.856485f, 0.866784f, 0.876393f,
                0.885352f, 0.893698f, 0.901468f, 0.908698f, 0.915420f,
                0.921669f, 0.927473f, 0.932862f, 0.937863f, 0.942503f,
                0.946806f, 0.950795f, 0.954492f, 0.957917f, 0.961090f,
                0.964028f, 0.966747f, 0.969265f, 0.971594f, 0.973749f,
                0.975743f, 0.977587f, 0.979293f, 0.980869f, 0.982327f,
                0.983675f, 0.984921f, 0.986072f, 0.987136f, 0.988119f,
                0.989027f, 0.989867f, 0.990642f, 0.991359f, 0.992020f,
                0.992631f, 0.993196f, 0.993718f, 0.994199f, 0.994644f,
                0.995055f, 0.995434f, 0.995784f, 0.996108f, 0.996407f,
                0.996682f, 0.996937f, 0.997172f, 0.997389f, 0.997590f,
                0.997775f, 0.997946f, 0.998104f, 0.998249f, 0.998384f,
                0.998508f, 0.998623f, 0.998728f, 0.998826f, 0.998916f,
                0.999000f, 0.999076f, 0.999147f, 0.999213f, 0.999273f,
                0.999329f, 0.999381f, 0.999428f, 0.999472f, 0.999513f,
                0.999550f, 0.999585f, 0.999617f, 0.999646f, 0.999673f,
                0.999699f, 0.999722f, 0.999743f, 0.999763f, 0.999781f,
                0.999798f, 0.999813f, 0.999828f, 0.999841f, 0.999853f,
                0.999865f, 0.999875f, 0.999885f, 0.999893f, 0.999902f,
                0.999909f, 0.999916f, 0.999923f, 0.999929f, 0.999934f,
                0.999939f, 0.999944f, 0.999948f, 0.999952f, 0.999956f,
                0.999959f, 0.999962f, 0.999965f, 0.999968f, 0.999970f,
                0.999973f, 0.999975f, 0.999977f, 0.999978f, 0.999980f,
                0.999982f, 0.999983f, 0.999984f, 0.999986f, 0.999987f,
                0.999988f, 0.999989f, 0.999990f, 0.999990f, 0.999991f,
                0.999992f, 0.999992f, 0.999993f, 0.999994f, 0.999994f,
                0.999994f, 0.999995f, 0.999995f, 0.999996f, 0.999996f,
                0.999996f, 0.999997f, 0.999997f, 0.999997f, 0.999997f,
                0.999997f, 0.999998f, 0.999998f, 0.999998f, 0.999998f,
                0.999998f, 0.999998f, 0.999999f, 0.999999f, 0.999999f,
                0.999999f, 0.999999f, 0.999999f, 0.999999f, 0.999999f,
                0.999999f, 0.999999f, 0.999999f, 0.999999f, 0.999999f,
                1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f,
                1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f,
                1.000000f,};

    // from opus_encoder.c
    /* Transition tables for the voice and music. First column is the
   middle (memoriless) threshold. The second column is the hysteresis
   (difference with the middle) */
    static final int[] mono_voice_bandwidth_thresholds = {
        11000, 1000, /* NB<->MB */
        14000, 1000, /* MB<->WB */
        17000, 1000, /* WB<->SWB */
        21000, 2000, /* SWB<->FB */};
    static final int[] mono_music_bandwidth_thresholds = {
        12000, 1000, /* NB<->MB */
        15000, 1000, /* MB<->WB */
        18000, 2000, /* WB<->SWB */
        22000, 2000, /* SWB<->FB */};
    static final int[] stereo_voice_bandwidth_thresholds = {
        11000, 1000, /* NB<->MB */
        14000, 1000, /* MB<->WB */
        21000, 2000, /* WB<->SWB */
        28000, 2000, /* SWB<->FB */};
    static final int[] stereo_music_bandwidth_thresholds = {
        12000, 1000, /* NB<->MB */
        18000, 2000, /* MB<->WB */
        21000, 2000, /* WB<->SWB */
        30000, 2000, /* SWB<->FB */};

    /* Threshold bit-rates for switching between mono and stereo */
    public static final int stereo_voice_threshold = 30000;
    public static final int stereo_music_threshold = 30000;

    /* Threshold bit-rate for switching between SILK/hybrid and CELT-only */
    static final int[][] mode_thresholds = {
        /* voice */ /* music */
        new int[]{64000, 16000}, /* mono */
        new int[]{36000, 16000}, /* stereo */};
}
