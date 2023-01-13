//
// Created by Justin on 13.12.2022.
//
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/timestamp.h>
#include <libavutil/opt.h>
#include <jni.h>

#include "transcode.c"

typedef struct {
    char copy_video;
    char copy_audio;
    char *output_extension;
    char *muxer_opt_key;
    char *muxer_opt_value;
    char *video_codec;
    char *audio_codec;
    char *codec_priv_key;
    char *codec_priv_value;
} StreamingParams;

typedef struct {
    AVFormatContext *avfc;
    AVCodec *video_avc;
    AVCodec *audio_avc;
    AVStream *video_avs;
    AVStream *audio_avs;
    AVCodecContext *video_avcc;
    AVCodecContext *audio_avcc;
    int video_index;
    int audio_index;
    char *filename;
} StreamingContext;

int open_media(const char *filename, AVFormatContext **avfc, char **errmsg) {
    *avfc = avformat_alloc_context();
    if(!*avfc) {
        *errmsg = "Failed to allocate memory for format\n";
        return -1;
    }

    if(avformat_open_input(avfc, filename, NULL, NULL) != 0) {
        *errmsg = "Failed to open input file\n";
        return -1;
    }

    if(avformat_find_stream_info(*avfc, NULL) < 0) {
        *errmsg = "Failed to get stream info\n";
        return -1;
    }

    return 0;
}

int fill_stream_info(AVStream *avs, AVCodec **avc, AVCodecContext **avcc, char **errmsg) {
    *avc = avcodec_find_decoder(avs->codecpar->codec_id);
    if(!*avc) {
        *errmsg ="Failed to find the codec\n";
        return -1;
    }

    *avcc = avcodec_alloc_context3(*avc);
    if(!*avcc) {
        *errmsg = "Failed to find the codec context\n";
        return -1;
    }

    if(avcodec_parameters_to_context(*avcc, avs->codecpar) < 0) {
        *errmsg ="Failed to fill the codec context\n";
        return -1;
    }

    if(avcodec_open2(*avcc, *avc, NULL) < 0) {
        *errmsg = "Failed to open codec\n";
        return -1;
    }

    return 0;
}

int prepare_decoder(StreamingContext *sc, char **errmsg) {
    for(size_t i=0;i<sc->avfc->nb_streams;i++) {
        AVCodecParameters *pLocalCodecParameters = sc->avfc->streams[i]->codecpar;
        AVCodec *pLocalCodec = avcodec_find_decoder(pLocalCodecParameters->codec_id);

        //VIDEO
        if(sc->avfc->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            sc->video_avs = sc->avfc->streams[i];
            sc->video_index = i;

            if(fill_stream_info(sc->video_avs, &sc->video_avc, &sc->video_avcc, errmsg)) {
                return -1;
            }
        }
            //AUDIO
        else if(sc->avfc->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            sc->audio_avs = sc->avfc->streams[i];
            sc->audio_index = i;

            if(fill_stream_info(sc->audio_avs, &sc->audio_avc, &sc->audio_avcc, errmsg)) {
                return -1;
            }
        }
            //OTHER
        else {
            printf("Skipping streams other than audio and video\n");
        }
        printf("\tCodec %s ID %d bit_rate %ld\n", pLocalCodec->name, pLocalCodec->id, pLocalCodecParameters->bit_rate);
    }

    return 0;
}

int prepare_copy(AVFormatContext *avfc, AVStream **avs, AVCodecParameters *decoder_par, char **errmsg) {
    *avs = avformat_new_stream(avfc, NULL);
    if(avcodec_parameters_copy((*avs)->codecpar, decoder_par) < 0) {
        *errmsg = "Falied to copy the parameters\n";
        return -1;
    }
    return 0;
}

int remux(AVPacket **pkt, AVFormatContext **avfc, AVRational decoder_tb, AVRational encoder_tb, char **errmsg) {
    av_packet_rescale_ts(*pkt, decoder_tb, encoder_tb);
    /*
    (*pkt)->pts = av_rescale_q_rnd((*pkt)->pts, decoder_tb, encoder_tb, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
    (*pkt)->dts = av_rescale_q_rnd((*pkt)->dts, decoder_tb, encoder_tb, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
    (*pkt)->duration = av_rescale_q((*pkt)->duration, decoder_tb, encoder_tb);
    */
    if(av_interleaved_write_frame(*avfc, *pkt) < 0) {
        *errmsg = "Error while copying stream packet";
        return -1;
    }
    //av_packet_unref(*pkt);
    return 0;
}



int merge_video_audio(char *video_filename, char *audio_filename, char *output_filename, char **errmsg) {

    StreamingContext *video_decoder = (StreamingContext *) calloc(1, sizeof(StreamingContext));
    video_decoder->filename = video_filename;
    StreamingContext *audio_decoder = (StreamingContext *) calloc(1, sizeof(StreamingContext));
    audio_decoder->filename = audio_filename;

    StreamingContext *encoder = (StreamingContext *) calloc(1, sizeof(StreamingContext));
    encoder->filename = output_filename;

    if(open_media(video_decoder->filename, &video_decoder->avfc, errmsg)) return -1;
    if(prepare_decoder(video_decoder, errmsg)) return -1;
    if(open_media(audio_decoder->filename, &audio_decoder->avfc, errmsg)) return -1;
    if(prepare_decoder(audio_decoder, errmsg)) return -1;

    printf("Videoforamt %s, duration %ld us, bit_rate %ld\n", video_decoder->avfc->iformat->name, video_decoder->avfc->duration, video_decoder->avfc->bit_rate);

    printf("Audioforamt %s, duration %ld us, bit_rate %ld\n", audio_decoder->avfc->iformat->name, audio_decoder->avfc->duration, audio_decoder->avfc->bit_rate);

    avformat_alloc_output_context2(&encoder->avfc, NULL, NULL, encoder->filename);
    if(!encoder->avfc) {
        *errmsg = "Could not allocate memory for output format\n";
        return -1;
    }

    if(prepare_copy(encoder->avfc, &encoder->video_avs, video_decoder->video_avs->codecpar, errmsg)) {
        return -1;
    }

    if(prepare_copy(encoder->avfc, &encoder->audio_avs, audio_decoder->audio_avs->codecpar, errmsg)) {
        return -1;
    }

    if(encoder->avfc->oformat->flags & AVFMT_GLOBALHEADER) {
        encoder->avfc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    if(!(encoder->avfc->oformat->flags & AVFMT_NOFILE)) {
        if(avio_open(&encoder->avfc->pb, encoder->filename, AVIO_FLAG_WRITE) < 0) {
            *errmsg = "Could not open the output file\n";
            return -1;
        }
    }

    AVDictionary *muxer_opts = NULL;

    if(avformat_write_header(encoder->avfc, &muxer_opts) < 0) {
        *errmsg="An error occurred when opening output file\n";
        return -1;
    }

    AVFrame *video_input_frame = av_frame_alloc();
    if(!video_input_frame) {
        *errmsg = "Failed to allocate memory for AVFrame\n";
        return -1;
    }

    AVPacket *video_input_packet = av_packet_alloc();
    if(!video_input_packet) {
        *errmsg = "Failed to allocate memory for AVPacket\n";
        return -1;
    }

    AVFrame *audio_input_frame = av_frame_alloc();
    if(!audio_input_frame) {
        *errmsg ="Failed to allocate memory for AVFrame\n";
        return -1;
    }

    AVPacket *audio_input_packet = av_packet_alloc();
    if(!audio_input_packet) {
        *errmsg = "Failed to allocate memory for AVPacket\n";
        return -1;
    }

    int continueReadVideo = 1;
    int continueReadAudio = 1;

    while(1) {

        if(continueReadVideo) {
            int readVideo = av_read_frame(video_decoder->avfc, video_input_packet);
            if(readVideo>=0) {
                if(video_decoder->avfc->streams[video_input_packet->stream_index]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
                    video_input_packet->stream_index = 0;
                    if(remux(&video_input_packet, &encoder->avfc, video_decoder->video_avs->time_base, encoder->video_avs->time_base, errmsg)) {
                        return -1;
                    }
                } else {
                    printf("Ignoring all non other packets in video decoder\n");
                }
            }
            if(readVideo < 0) {
                continueReadVideo = 0;
            }
        }

        if(continueReadAudio) {
            int readAudio = av_read_frame(audio_decoder->avfc, audio_input_packet);
            if(readAudio>=0) {
                if(audio_decoder->avfc->streams[audio_input_packet->stream_index]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
                    audio_input_packet->stream_index = 1;
                    if(remux(&audio_input_packet, &encoder->avfc, audio_decoder->audio_avs->time_base, encoder->audio_avs->time_base, errmsg)) {
                        return -1;
                    }
                } else {
                    printf("Ignoring all non other packets in audio decoder\n");
                }
            }
            if(readAudio < 0) {
                continueReadAudio = 0;
            }
        }

        if((!continueReadVideo) && (!continueReadAudio)) break;
    }

    //FREE

    av_write_trailer(encoder->avfc);

    if(muxer_opts != NULL) {
        av_dict_free(&muxer_opts);
        muxer_opts = NULL;
    }

    if(video_input_frame != NULL) {
        av_frame_free(&video_input_frame);
        video_input_frame = NULL;
    }

    if(video_input_packet != NULL) {
        av_packet_free(&video_input_packet);
        video_input_packet = NULL;
    }

    if(audio_input_frame != NULL) {
        av_frame_free(&audio_input_frame);
        audio_input_frame = NULL;
    }

    if(audio_input_packet != NULL) {
        av_packet_free(&audio_input_packet);
        audio_input_packet = NULL;
    }

    avformat_close_input(&video_decoder->avfc);
    avformat_close_input(&audio_decoder->avfc);

    avformat_free_context(video_decoder->avfc);
    video_decoder->avfc = NULL;
    avformat_free_context(audio_decoder->avfc);
    audio_decoder->avfc = NULL;
    avformat_free_context(encoder->avfc);
    encoder->avfc = NULL;

    avcodec_free_context(&video_decoder->video_avcc);
    video_decoder->video_avcc = NULL;
    avcodec_free_context(&audio_decoder->video_avcc);
    audio_decoder->video_avcc = NULL;
    avcodec_free_context(&encoder->video_avcc);
    encoder->video_avcc = NULL;

    free(video_decoder);
    video_decoder = NULL;
    free(audio_decoder);
    audio_decoder = NULL;
    free(encoder);
    encoder = NULL;

    return 0;
}

#define STRING(s) ((*env)->NewStringUTF(env, s));

JNIEXPORT jstring JNICALL
Java_com_jschartner_youtube_Ffmpeg_mergeVideoAudio(JNIEnv *env, jclass clazz, jstring video_path,
                                                   jstring audio_path, jstring output_path) {
    char *video = (*env)->GetStringUTFChars(env, video_path, 0);
    char *audio = (*env)->GetStringUTFChars(env, audio_path, 0);
    char *output = (*env)->GetStringUTFChars(env, output_path, 0);

    char *errmsg = NULL;

    int res =  merge_video_audio(video, audio, output, &errmsg);
    if(res) {
        if(errmsg == NULL) {
            return STRING("Somehow no error is set");
        }
        else {
            return STRING(errmsg);
        }
    }

    return STRING("All went fine");
}

JNIEXPORT jstring JNICALL
Java_com_jschartner_youtube_Ffmpeg_getVideoCodec(JNIEnv *env, jclass clazz, jstring filepath) {
    char *file_path = (*env)->GetStringUTFChars(env, filepath, 0);

    AVFormatContext *pFormatContext = avformat_alloc_context();
    if(!pFormatContext) return STRING("ERROR: avformat_alloc_context");

    if(avformat_open_input(&pFormatContext, file_path, NULL, NULL) != 0)
      return STRING("ERROR: avformat_open_input");

    jstring str = STRING(pFormatContext->iformat->name);


    avformat_close_input(&pFormatContext);    
    return str;
}

JNIEXPORT jstring JNICALL
Java_com_jschartner_youtube_Ffmpeg_transcodeToMp3(JNIEnv *env, jclass clazz, jstring input_path,
                                                  jstring output_path) {
    char *input = (*env)->GetStringUTFChars(env, input_path, 0);
    char *output = (*env)->GetStringUTFChars(env, output_path, 0);

    char *errmsg = NULL;

    int res =  transcode_to_mp3(input, output, &errmsg);
    if(res) {
        if(errmsg == NULL) {
            return STRING("Somehow no error is set");
        }
        else {
            return STRING(errmsg);
        }
    }

    return STRING("Finished transcoding");
}