//
// Created by Justin on 13.12.2022.
//
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/timestamp.h>
#include <libavutil/opt.h>
#include <jni.h>

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

int open_media(const char *filename, AVFormatContext **avfc) {
    *avfc = avformat_alloc_context();
    if(!*avfc) {
        printf("Failed to allocate memory for format\n");
        return -1;
    }

    if(avformat_open_input(avfc, filename, NULL, NULL) != 0) {
        printf("Failed to open input file %s\n", filename);
        return -1;
    }

    if(avformat_find_stream_info(*avfc, NULL) < 0) {
        printf("Failed to get stream info\n");
        return -1;
    }

    return 0;
}

int fill_stream_info(AVStream *avs, AVCodec **avc, AVCodecContext **avcc) {
    *avc = avcodec_find_decoder(avs->codecpar->codec_id);
    if(!*avc) {
        printf("Failed to find the codec\n");
        return -1;
    }

    *avcc = avcodec_alloc_context3(*avc);
    if(!*avcc) {
        printf("Failed to find the codec context\n");
        return -1;
    }

    if(avcodec_parameters_to_context(*avcc, avs->codecpar) < 0) {
        printf("Failed to fill the codec context\n");
        return -1;
    }

    if(avcodec_open2(*avcc, *avc, NULL) < 0) {
        printf("Failed to open codec\n");
        return -1;
    }

    return 0;
}

int prepare_decoder(StreamingContext *sc) {
    for(size_t i=0;i<sc->avfc->nb_streams;i++) {
        AVCodecParameters *pLocalCodecParameters = sc->avfc->streams[i]->codecpar;
        AVCodec *pLocalCodec = avcodec_find_decoder(pLocalCodecParameters->codec_id);

        //VIDEO
        if(sc->avfc->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            sc->video_avs = sc->avfc->streams[i];
            sc->video_index = i;

            printf("Video Codec: resolution %d x %d\n", pLocalCodecParameters->width, pLocalCodecParameters->height);
            printf("AVStream->time_base before open coded %d/%d\n", sc->avfc->streams[i]->time_base.num,
                   sc->avfc->streams[i]->time_base.den);
            printf("AVStream->r_frame_rate before open coded %d/%d\n", sc->avfc->streams[i]->r_frame_rate.num,
                   sc->avfc->streams[i]->r_frame_rate.num);
            printf("AVStream->start_time %" PRId64 "\n", sc->avfc->streams[i]->start_time);
            printf("AVStream->duration %" PRId64 "\n", sc->avfc->streams[i]->duration);

            if(fill_stream_info(sc->video_avs, &sc->video_avc, &sc->video_avcc)) {
                return -1;
            }
        }
            //AUDIO
        else if(sc->avfc->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            sc->audio_avs = sc->avfc->streams[i];
            sc->audio_index = i;

            printf("Audio Codec: %d channels, sample rate %d\n", pLocalCodecParameters->channels, pLocalCodecParameters->sample_rate);
            printf("AVStream->time_base before open coded %d/%d\n", sc->avfc->streams[i]->time_base.num,
                   sc->avfc->streams[i]->time_base.den);
            printf("AVStream->r_frame_rate before open coded %d/%d\n", sc->avfc->streams[i]->r_frame_rate.num,
                   sc->avfc->streams[i]->r_frame_rate.num);
            printf("AVStream->start_time %" PRId64 "\n", sc->avfc->streams[i]->start_time);
            printf("AVStream->duration %" PRId64 "\n", sc->avfc->streams[i]->duration);

            if(fill_stream_info(sc->audio_avs, &sc->audio_avc, &sc->audio_avcc)) {
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

int prepare_copy(AVFormatContext *avfc, AVStream **avs, AVCodecParameters *decoder_par) {
    *avs = avformat_new_stream(avfc, NULL);
    avcodec_parameters_copy((*avs)->codecpar, decoder_par);
    return 0;
}

int remux(AVPacket **pkt, AVFormatContext **avfc, AVRational decoder_tb, AVRational encoder_tb) {
    av_packet_rescale_ts(*pkt, decoder_tb, encoder_tb);
    /*
    (*pkt)->pts = av_rescale_q_rnd((*pkt)->pts, decoder_tb, encoder_tb, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
    (*pkt)->dts = av_rescale_q_rnd((*pkt)->dts, decoder_tb, encoder_tb, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
    (*pkt)->duration = av_rescale_q((*pkt)->duration, decoder_tb, encoder_tb);
    */
    if(av_interleaved_write_frame(*avfc, *pkt) < 0) {
        printf("Error while copying stream packet");
        return -1;
    }
    //av_packet_unref(*pkt);
    return 0;
}



int merge_video_audio(char *video_filename, char *audio_filename, char *output_filename) {

    StreamingContext *video_decoder = (StreamingContext *) calloc(1, sizeof(StreamingContext));
    video_decoder->filename = video_filename;
    StreamingContext *audio_decoder = (StreamingContext *) calloc(1, sizeof(StreamingContext));
    audio_decoder->filename = audio_filename;

    StreamingContext *encoder = (StreamingContext *) calloc(1, sizeof(StreamingContext));
    encoder->filename = output_filename;

    if(open_media(video_decoder->filename, &video_decoder->avfc)) return -1;
    if(prepare_decoder(video_decoder)) return -1;
    if(open_media(audio_decoder->filename, &audio_decoder->avfc)) return -1;
    if(prepare_decoder(audio_decoder)) return -1;

    printf("Videoforamt %s, duration %ld us, bit_rate %ld\n", video_decoder->avfc->iformat->name, video_decoder->avfc->duration, video_decoder->avfc->bit_rate);

    printf("Audioforamt %s, duration %ld us, bit_rate %ld\n", audio_decoder->avfc->iformat->name, audio_decoder->avfc->duration, audio_decoder->avfc->bit_rate);

    avformat_alloc_output_context2(&encoder->avfc, NULL, NULL, encoder->filename);
    if(!encoder->avfc) {
        printf("Could not allocate memory for output format\n");
        return -1;
    }

    if(prepare_copy(encoder->avfc, &encoder->video_avs, video_decoder->video_avs->codecpar)) {
        return -1;
    }

    if(prepare_copy(encoder->avfc, &encoder->audio_avs, audio_decoder->audio_avs->codecpar)) {
        return -1;
    }

    if(encoder->avfc->oformat->flags & AVFMT_GLOBALHEADER) {
        encoder->avfc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    if(!(encoder->avfc->oformat->flags & AVFMT_NOFILE)) {
        if(avio_open(&encoder->avfc->pb, encoder->filename, AVIO_FLAG_WRITE) < 0) {
            printf("Could not open the output file\n");
            return -1;
        }
    }

    AVDictionary *muxer_opts = NULL;

    if(avformat_write_header(encoder->avfc, &muxer_opts) < 0) {
        printf("An error occurred when opening output file\n");
        return -1;
    }

    AVFrame *video_input_frame = av_frame_alloc();
    if(!video_input_frame) {
        printf("Failed to allocate memory for AVFrame\n");
        return -1;
    }

    AVPacket *video_input_packet = av_packet_alloc();
    if(!video_input_packet) {
        printf("Failed to allocate memory for AVPacket\n");
        return -1;
    }

    AVFrame *audio_input_frame = av_frame_alloc();
    if(!audio_input_frame) {
        printf("Failed to allocate memory for AVFrame\n");
        return -1;
    }

    AVPacket *audio_input_packet = av_packet_alloc();
    if(!audio_input_packet) {
        printf("Failed to allocate memory for AVPacket\n");
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
                    if(remux(&video_input_packet, &encoder->avfc, video_decoder->video_avs->time_base, encoder->video_avs->time_base)) {
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
                    if(remux(&audio_input_packet, &encoder->avfc, audio_decoder->audio_avs->time_base, encoder->audio_avs->time_base)) {
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

JNIEXPORT jint JNICALL
Java_com_jschartner_youtube_Ffmpeg_mergeVideoAudio(JNIEnv *env, jclass clazz, jstring video_path,
                                                   jstring audio_path, jstring output_path) {
    char *video = (*env)->GetStringUTFChars(env, video_path, 0);
    char *audio = (*env)->GetStringUTFChars(env, audio_path, 0);
    char *output = (*env)->GetStringUTFChars(env, output_path, 0);

    return merge_video_audio(video, audio, output);
}